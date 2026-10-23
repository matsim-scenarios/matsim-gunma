#!/usr/bin/env python3
"""Sequential, restartable v1.9 calibration and matched policy experiment."""
import argparse
import copy
import hashlib
import fcntl
import json
import math
import os
from pathlib import Path
import shlex
import subprocess
import sys
import xml.etree.ElementTree as ET

import numpy as np
import optuna
import pandas as pd
from matsim.calibration import ASCCalibrator, CalibrationSampler, utils
from matsim.calibration.analysis import read_trips_and_persons
from calibrate_age_asc import (AGE_GROUPS, MODES, AdvancedASCGroupCalibrator,
                               age_group, filter_persons, filter_modes, read_age_target)

ROOT = Path(__file__).resolve().parents[3]
TARGET = {'walk': .117, 'bike': .086, 'car': .682, 'ride': .115}


def parameter(parent, name, value):
    node = parent.find(f"param[@name='{name}']")
    if node is None:
        node = ET.SubElement(parent, 'param', name=name)
    node.set('value', str(value))


def module(root, name):
    node = root.find(f"module[@name='{name}']")
    return node if node is not None else ET.SubElement(root, 'module', name=name)


def load_config(path):
    """Resolve file inputs before moving configurations between stage directories."""
    tree = ET.parse(path)
    for node in tree.getroot().iter('param'):
        name, value = node.get('name'), node.get('value', '')
        if (name.endswith('File') or name.endswith('Filename')) and value not in ('', 'null', 'true', 'false'):
            if not Path(value).is_absolute() and '://' not in value:
                node.set('value', str((path.parent / value).resolve()))
    return tree


def mode_constants(tree):
    result = {}
    for node in module(tree.getroot(), 'scoring').findall("parameterset[@type='scoringParameters']/parameterset[@type='modeParams']"):
        result[node.find("param[@name='mode']").get('value')] = float(node.find("param[@name='constant']").get('value'))
    return result


def apply_patch(tree, patch):
    """Apply the small calibrator parameter dictionary to a full MATSim XML config."""
    root = tree.getroot()
    for name, contents in patch.items():
        group = module(root, 'scoring' if name == 'planCalcScore' else name)
        if name == 'advancedScoring':
            for old in list(group.findall("parameterset[@type='scoringParameters']")):
                group.remove(old)
        for scoring in contents['scoringParameters']:
            if name == 'advancedScoring':
                ps = ET.SubElement(group, 'parameterset', type='scoringParameters')
                for key, value in scoring.items():
                    if key != 'modeParams':
                        parameter(ps, key, value)
            else:
                ps = group.find("parameterset[@type='scoringParameters']")
                if ps is None:
                    ps = ET.SubElement(group, 'parameterset', type='scoringParameters')
            for values in scoring['modeParams']:
                mp = next((p for p in ps.findall("parameterset[@type='modeParams']")
                           if p.find("param[@name='mode']").get('value') == values['mode']), None)
                if mp is None:
                    mp = ET.SubElement(ps, 'parameterset', type='modeParams')
                for key, value in values.items():
                    parameter(mp, key, value)


def save_config(tree, path):
    ET.indent(tree, space='\t')
    path.write_text('<?xml version="1.0" encoding="UTF-8"?>\n'
                    '<!DOCTYPE config SYSTEM "http://www.matsim.org/files/dtd/config_v2.dtd">\n'
                    + ET.tostring(tree.getroot(), encoding='unicode') + '\n')


def complete_trials(study):
    return [t for t in study.trials if t.state == optuna.trial.TrialState.COMPLETE
            and t.values and all(math.isfinite(v) for v in t.values)]


def best_trial(study):
    trials = complete_trials(study)
    if not trials:
        raise ValueError('No completed finite calibration trial available')
    return min(trials, key=lambda t: (float(np.mean(t.values)), t.number))


class OverallCalibrator(ASCCalibrator):
    def update_config(self, study, trial, prefix, config):
        for mode in self.modes:
            value = 0.0 if mode == self.fixed_mode else trial.suggest_float(
                prefix + mode, -sys.float_info.max, sys.float_info.max)
            self.get_mode_params(config, mode)['constant'] = value

    def calc_stats(self, trial, run_dir, transform_persons=None, transform_trips=None):
        trips, _ = read_trips_and_persons(run_dir, transform_persons, transform_trips)
        if trips.empty:
            raise ValueError('No calibration trips')
        shares = trips.main_mode.value_counts(normalize=True).reindex(MODES, fill_value=0.)
        errors = []
        for mode in MODES:
            error = abs(float(shares[mode]) - TARGET[mode])
            trial.set_user_attr(f'{mode}_share', float(shares[mode]))
            trial.set_user_attr(f'{mode}_mae', error)
            errors.append(error)
        return errors


def verify_effective_config(config, output, iterations):
    expected = ET.parse(config)
    actual = ET.parse(next(output.glob('*.output_config.xml')))
    def value(tree, group, name):
        return tree.find(f"module[@name='{group}']/param[@name='{name}']").get('value')
    if int(value(actual, 'controller', 'lastIteration')) != iterations:
        raise ValueError('Effective iteration count differs from requested budget')
    for group, name in [('qsim', 'flowCapacityFactor'), ('qsim', 'storageCapacityFactor'),
                        ('counts', 'countsScaleFactor')]:
        if not math.isclose(float(value(actual, group, name)), .01):
            raise ValueError(f'Effective {group}/{name} is not scaled to 1%')
    if mode_constants(expected) != mode_constants(actual):
        raise ValueError('Runtime overwrote the calibrated overall ASCs')
    def deltas(tree):
        result = {}
        for ps in tree.findall("module[@name='advancedScoring']/parameterset[@type='scoringParameters']"):
            group = ps.find("param[@name='age_group']").get('value')
            for mp in ps.findall("parameterset[@type='modeParams']"):
                mode = mp.find("param[@name='mode']").get('value')
                result[group, mode] = float(mp.find("param[@name='deltaConstant']").get('value'))
        return result
    if deltas(expected) != deltas(actual):
        raise ValueError('Runtime overwrote the calibrated age ASCs')


def java_run(args, config, output, policy=None):
    if output.exists():
        raise ValueError(f'Refusing to overwrite existing output: {output}')
    main = 'org.matsim.run.GunmaPolicyScenario' if policy else 'org.matsim.run.GunmaBaseScenario'
    command = ['java', '-Djava.awt.headless=true', *shlex.split(args.jvm_args), '-cp', str(args.jar), main,
               'run', '--config', str(config), '--1pct', '--iterations', str(args.iterations),
               '--output', str(output), '--runId', 'gunma-v1.9-1pct']
    if policy:
        command += ['--policy-case', policy]
    with config.with_suffix('.log').open('w') as log:
        subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
    verify_effective_config(config, output, args.iterations)


def signature(args, source):
    return dict(config_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                jar_sha256=hashlib.sha256(args.jar.read_bytes()).hexdigest(),
                target_sha256=hashlib.sha256((ROOT / 'resources/mode_share_per_group_dist_ref.csv').read_bytes()).hexdigest(),
                iterations=args.iterations, sample='1pct', seed=args.seed)


def calibrate(args, stage, source):
    directory = args.work / stage
    directory.mkdir(parents=True, exist_ok=True)
    base = load_config(source)
    root = base.getroot()
    parameter(module(root, 'controller'), 'lastIteration', args.iterations)
    parameter(module(root, 'global'), 'randomSeed', args.seed)
    # Start age deltas from zero; overall calibration has no age adjustments.
    advanced = module(root, 'advancedScoring')
    for old in list(advanced.findall("parameterset[@type='scoringParameters']")):
        advanced.remove(old)
    initial = mode_constants(base)
    rate = utils.linear_scheduler(start=.3, interval=30)
    calibrator = (OverallCalibrator(MODES, initial, TARGET, lr=rate) if stage == 'overall'
                  else AdvancedASCGroupCalibrator(MODES, initial, read_age_target(
                      ROOT / 'resources/mode_share_per_group_dist_ref.csv'), lr=rate))
    study = optuna.create_study(study_name=f'gunma-v1.9-{stage}',
        storage=f'sqlite:///{directory / "study.db"}', load_if_exists=True,
        directions=['minimize'] * calibrator.num_targets, sampler=CalibrationSampler([calibrator]))
    expected = signature(args, source)
    previous = study.user_attrs.get('inputs')
    if previous is not None and previous != expected:
        raise ValueError('Study inputs changed; use a separate --work directory')
    study.set_user_attr('inputs', expected)
    calibrator.init_study(study)
    # A killed process leaves RUNNING trials. Invocation is strictly single-process.
    for trial in study.trials:
        if trial.state == optuna.trial.TrialState.RUNNING:
            study.tell(trial.number, state=optuna.trial.TrialState.FAIL)

    def objective(trial):
        tree = copy.deepcopy(base)
        patch = {}
        calibrator.update_config(study, trial, calibrator.name + '-', patch)
        apply_patch(tree, patch)
        trial.set_user_attr('patch', patch)
        config = directory / f'trial-{trial.number:03d}.xml'
        output = directory / f'run-{trial.number:03d}'
        # Match the existing chain scheduler, keeping the latest chosen warm start.
        completed = complete_trials(study)
        chained = study.user_attrs.get('last_chained_plans')
        if completed and utils.default_chain_scheduler(completed):
            paths = sorted((directory / f'run-{completed[-1].number:03d}').glob('*.output_plans.xml.gz'))
            if paths:
                chained = str(paths[0])
                study.set_user_attr('last_chained_plans', chained)
        if chained:
            parameter(module(tree.getroot(), 'plans'), 'inputPlansFile', chained)
        save_config(tree, config)
        java_run(args, config, output)
        errors = calibrator.calc_stats(trial, str(output), filter_persons, filter_modes)
        if not all(math.isfinite(v) for v in errors):
            raise ValueError('Non-finite calibration errors')
        return [float(value) for value in errors]

    remaining = max(0, args.trials - len(complete_trials(study)))
    if remaining:
        study.optimize(objective, n_trials=remaining)
    selected = best_trial(study)
    # Export original population inputs, never chained trial plans.
    apply_patch(base, selected.user_attrs['patch'])
    save_config(base, directory / 'selected-config.xml')
    history = [dict(trial=t.number, mae=float(np.mean(t.values)), errors=t.values)
               for t in complete_trials(study)]
    pd.DataFrame(history).drop(columns='errors').to_csv(directory / 'convergence.csv', index=False)
    fit = []
    if stage == 'overall':
        for mode in MODES:
            observed = selected.user_attrs[f'{mode}_share']
            fit.append(dict(mode=mode, target=TARGET[mode], observed=observed,
                            absolute_error=abs(observed - TARGET[mode])))
    else:
        for _, row in calibrator.target.iterrows():
            observed = selected.user_attrs[f'[age_group={row.age_group}]-{row["mode"]}_share']
            fit.append(dict(age_group=row.age_group, mode=row['mode'], target=row.share,
                            observed=observed, absolute_error=abs(observed - row.share)))
    pd.DataFrame(fit).to_csv(directory / 'fit.csv', index=False)
    metadata = dict(stage=stage, trial=selected.number, mae=float(np.mean(selected.values)),
                    source_config=str(source), inputs=expected, parameters=selected.user_attrs['patch'],
                    history=history, converged='Inspect convergence.csv; budget completion is not convergence')
    (directory / 'selection.json').write_text(json.dumps(metadata, indent=2) + '\n')


def policy_report(directory):
    records, violations = [], {}
    for case in ['baseline', 'policy']:
        trips, persons = read_trips_and_persons(str(directory / case), filter_persons)
        trips['age_group'] = trips.age.map(age_group)
        for group, subset in [('all', trips), ('75+', trips[trips.age >= 75]),
                               *[(g, trips[trips.age_group == g]) for g in AGE_GROUPS]]:
            if subset.empty:
                raise ValueError(f'No trips for {case}/{group}')
            shares = subset.main_mode.value_counts(normalize=True).reindex(MODES, fill_value=0.)
            for mode, share in shares.items():
                records.append(dict(case=case, age_group=group, mode=mode, share=share, trips=len(subset)))
        violations[case] = int(((trips.age >= 75) & (trips.main_mode == 'car')).sum())
        # Check every executed leg too, including car legs within multimodal trips.
        legs_file = next((directory / case).glob('*.output_legs.csv.gz'))
        legs = pd.read_csv(legs_file, sep=';', dtype={'person': str})
        affected = set(persons.loc[persons.age >= 75, 'person'])
        violations[case + '_car_legs'] = int((legs.person.isin(affected) & legs['mode'].eq('car')).sum())
    frame = pd.DataFrame(records)
    frame.to_csv(directory / 'mode-shares.csv', index=False)
    comparison = frame.pivot(index=['age_group', 'mode'], columns='case', values='share')
    comparison['change'] = comparison.policy - comparison.baseline
    comparison.to_csv(directory / 'mode-share-changes.csv')
    (directory / 'driving-check.json').write_text(json.dumps(violations, indent=2) + '\n')
    if violations['policy'] or violations['policy_car_legs']:
        raise ValueError('Restricted residents still drive; see driving-check.json')


def policy(args, source):
    directory = args.work / 'comparison'
    directory.mkdir(parents=True, exist_ok=True)
    manifest = directory / 'inputs.json'
    expected = signature(args, source)
    if manifest.exists() and json.loads(manifest.read_text()) != expected:
        raise ValueError('Policy inputs changed; use a separate --work directory')
    manifest.write_text(json.dumps(expected, indent=2) + '\n')
    for case, switch in [('baseline', 'noCarAvailOver75base'), ('policy', 'noCarAvailOver75policy')]:
        marker = directory / f'{case}.complete'
        if marker.exists():
            continue
        tree = load_config(source)
        parameter(module(tree.getroot(), 'global'), 'randomSeed', args.seed)
        parameter(module(tree.getroot(), 'controller'), 'lastIteration', args.iterations)
        config = directory / f'{case}.xml'
        save_config(tree, config)
        java_run(args, config, directory / case, switch)
        marker.touch()
    policy_report(directory)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=['overall', 'age', 'policy', 'all'])
    parser.add_argument('--config', type=Path, default=ROOT / 'input/v1.9/gunma-v1.9-config.xml')
    parser.add_argument('--policy-config', type=Path, help='Use an existing calibrated configuration for the policy comparison')
    parser.add_argument('--work', type=Path, default=ROOT / 'experiments/v1.9/results')
    parser.add_argument('--jar', type=Path, default=ROOT / 'matsim-gunma-1.x-SNAPSHOT.jar')
    parser.add_argument('--iterations', type=int, default=300)
    parser.add_argument('--trials', type=int, default=15)
    parser.add_argument('--seed', type=int, default=4711)
    parser.add_argument('--jvm-args', default=os.environ.get('GUNMA_CALIB_JVM_ARGS', '-Xmx55G -Xms55G -XX:+AlwaysPreTouch -XX:+UseParallelGC'))
    args = parser.parse_args()
    args.work, args.jar = args.work.resolve(), args.jar.resolve()
    if args.iterations < 0 or args.trials < 1:
        parser.error('iterations must be nonnegative and trials must be positive')
    if not args.jar.is_file():
        parser.error(f'Build the scenario JAR first: {args.jar}')
    args.work.mkdir(parents=True, exist_ok=True)
    lock = (args.work / '.lock').open('w')
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        parser.error('Another process is using this experiment directory')
    for stage in (['overall', 'age', 'policy'] if args.stage == 'all' else [args.stage]):
        source = (args.config.resolve() if stage == 'overall' else
                  args.work / ('overall' if stage == 'age' else 'age') / 'selected-config.xml')
        if stage == 'policy' and args.policy_config is not None:
            source = args.policy_config.resolve()
        if not source.is_file():
            parser.error(f'Complete the preceding calibration stage first: {source}')
        if stage == 'policy':
            policy(args, source)
        else:
            calibrate(args, stage, source)


if __name__ == '__main__':
    main()
