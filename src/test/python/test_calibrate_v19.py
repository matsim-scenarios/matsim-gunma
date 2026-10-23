import copy
from pathlib import Path
import sys
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'main/python'))
import optuna
import pandas as pd
import calibrate_v19 as workflow
from calibrate_age_asc import AGE_GROUPS, AdvancedASCGroupCalibrator, age_group, read_age_target


class CalibrationTest(unittest.TestCase):
    def test_age_boundaries_and_targets(self):
        for age, group in [(0, '0-17'), (17, '0-17'), (18, '18-29'), (29, '18-29'),
                           (30, '30-44'), (44, '30-44'), (45, '45-64'), (64, '45-64'),
                           (65, '65-74'), (74, '65-74'), (75, '75-84'), (84, '75-84'), (85, '85+')]:
            self.assertEqual(age_group(age), group)
        target = read_age_target(workflow.ROOT / 'resources/mode_share_per_group_dist_ref.csv')
        self.assertEqual(set(target.age_group), set(AGE_GROUPS))
        self.assertEqual(len(target), 28)
        for value in target.groupby('age_group').share.sum():
            self.assertAlmostEqual(value, 1.)

    def test_export_and_zero_age_initialization(self):
        tree = workflow.load_config(workflow.ROOT / 'input/v1.9/gunma-v1.9-config.xml')
        self.assertEqual(tree.find("module[@name='plans']/param[@name='insistingOnUsingDeprecatedPersonAttributeFile']").get("value"), "false")
        initial = workflow.mode_constants(tree)
        self.assertEqual(initial['car'], -1.127816)
        self.assertEqual(tree.find("module[@name='controller']/param[@name='lastIteration']").get('value'), '300')
        target = read_age_target(workflow.ROOT / 'resources/mode_share_per_group_dist_ref.csv')
        calibrator = AdvancedASCGroupCalibrator(workflow.MODES, initial, target)
        study = optuna.create_study(directions=['minimize'] * 28,
                                   sampler=workflow.CalibrationSampler([calibrator]))
        trial = study.ask()
        values = {}
        calibrator.update_config(study, trial, 'age_asc-', values)
        workflow.apply_patch(tree, values)
        self.assertEqual(workflow.mode_constants(tree), initial)
        deltas = tree.findall("module[@name='advancedScoring']/parameterset/parameterset/param[@name='deltaConstant']")
        self.assertEqual(len(deltas), 28)
        self.assertTrue(all(float(n.get('value')) == 0 for n in deltas))
        # Negative ASC values must survive sampler boundaries and XML handoff.
        patch_values = {'planCalcScore': {'scoringParameters': [{'modeParams': [
            {'mode': 'car', 'constant': -2.5}, {'mode': 'walk', 'constant': 0.}]}]}}
        workflow.apply_patch(tree, patch_values)
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'selected.xml'
            workflow.save_config(tree, path)
            reread = workflow.load_config(path)
            self.assertEqual(workflow.mode_constants(reread)['car'], -2.5)
            for name in ('network', 'facilities', 'plans', 'vehicles'):
                mod = reread.find(f"module[@name='{name}']")
                self.assertTrue(any(n.get('value', '').startswith('/') for n in mod.findall('param')))

    def test_best_trial_excludes_failures_and_breaks_ties(self):
        def trial(number, values, state=optuna.trial.TrialState.COMPLETE):
            return SimpleNamespace(number=number, values=values, state=state)
        study = SimpleNamespace(trials=[trial(4, [.1, .1]), trial(2, [.1, .1]),
            trial(0, [0, 0], optuna.trial.TrialState.FAIL), trial(1, [float('nan'), 0])])
        self.assertEqual(workflow.best_trial(study).number, 2)
        with self.assertRaises(ValueError):
            workflow.best_trial(SimpleNamespace(trials=[]))

    def test_missing_age_observations_fail(self):
        target = read_age_target(workflow.ROOT / 'resources/mode_share_per_group_dist_ref.csv')
        calibrator = AdvancedASCGroupCalibrator(workflow.MODES, {}, target)
        trips = pd.DataFrame({'age_group': ['0-17'], 'main_mode': ['walk']})
        with patch('calibrate_age_asc.read_trips_and_persons', return_value=(trips, None)):
            with self.assertRaisesRegex(ValueError, 'No observed trips'):
                calibrator.calc_stats(SimpleNamespace(set_user_attr=lambda *args: None), 'unused')

    def test_structural_zero_target(self):
        target = read_age_target(workflow.ROOT / 'resources/mode_share_per_group_dist_ref.csv')
        calibrator = AdvancedASCGroupCalibrator(workflow.MODES, {}, target)
        trial = SimpleNamespace(user_attrs={'[age_group=0-17]-car_share': 0.,
                                           '[age_group=0-17]-walk_share': .4})
        self.assertEqual(calibrator.update_step('[age_group=0-17]-car', trial, []), 0.)
        trial.user_attrs['[age_group=0-17]-car_share'] = .1
        with self.assertRaisesRegex(ValueError, 'zero-target group'):
            calibrator.update_step('[age_group=0-17]-car', trial, [])

    def test_age_objective_returns_optuna_sequence(self):
        import numpy as np
        with tempfile.TemporaryDirectory() as tmp:
            args = SimpleNamespace(work=Path(tmp), iterations=300, seed=4711,
                                   trials=1, jar=Path(tmp) / 'test.jar')
            args.jar.write_bytes(b'test')
            source = workflow.ROOT / 'input/v1.9/gunma-v1.9-config.xml'
            def stats(calibrator, trial, *unused):
                for _, row in calibrator.target.iterrows():
                    trial.set_user_attr(f'[age_group={row.age_group}]-{row["mode"]}_share', row.share)
                return np.zeros(28)
            with patch.object(workflow, 'java_run'), patch.object(
                    workflow.AdvancedASCGroupCalibrator, 'calc_stats', stats):
                workflow.calibrate(args, 'age', source)
            self.assertTrue((args.work / 'age/selected-config.xml').is_file())

    def test_effective_configuration_verification(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            tree = workflow.load_config(workflow.ROOT / 'input/v1.9/gunma-v1.9-config.xml')
            for group, name in [('qsim', 'flowCapacityFactor'), ('qsim', 'storageCapacityFactor'),
                                ('counts', 'countsScaleFactor')]:
                workflow.parameter(workflow.module(tree.getroot(), group), name, .01)
            source = directory / 'input.xml'
            actual = directory / 'test.output_config.xml'
            workflow.save_config(tree, source)
            workflow.save_config(tree, actual)
            workflow.verify_effective_config(source, directory, 300)
            workflow.parameter(workflow.module(tree.getroot(), 'controller'), 'lastIteration', 500)
            workflow.save_config(tree, actual)
            with self.assertRaisesRegex(ValueError, 'iteration count'):
                workflow.verify_effective_config(source, directory, 300)

    def test_policy_report_checks_executed_legs(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            persons = pd.DataFrame({'person': [f'gunma_{n}' for n in range(7)],
                                    'age': [10, 20, 35, 50, 70, 80, 90],
                                    'first_act_x': [0] * 7, 'first_act_y': [0] * 7})
            for case in ['baseline', 'policy']:
                output = directory / case
                output.mkdir()
                modes = ['walk'] * 5 + (['car'] * 2 if case == 'baseline' else ['walk'] * 2)
                trips = pd.DataFrame({'person': persons.person, 'main_mode': modes,
                                      'longest_distance_mode': modes})
                legs = pd.DataFrame({'person': persons.person, 'mode': modes})
                persons.to_csv(output / 'test.output_persons.csv.gz', sep=';', index=False)
                trips.to_csv(output / 'test.output_trips.csv.gz', sep=';', index=False)
                legs.to_csv(output / 'test.output_legs.csv.gz', sep=';', index=False)
            workflow.policy_report(directory)
            import json
            counts = json.loads((directory / 'driving-check.json').read_text())
            self.assertEqual(counts['baseline'], 2)
            self.assertEqual(counts['policy'], 0)
            self.assertEqual(counts['policy_car_legs'], 0)
            legs.loc[6, 'mode'] = 'car'
            legs.to_csv(directory / 'policy/test.output_legs.csv.gz', sep=';', index=False)
            with self.assertRaisesRegex(ValueError, 'still drive'):
                workflow.policy_report(directory)

    def test_existing_policy_configuration_skips_calibration(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            jar = directory / 'scenario.jar'
            jar.write_bytes(b'test')
            source = workflow.ROOT / 'experiments/2026-10-23c-oldagebasedparams-base-policy/oldage-config.xml'
            argv = ['calibrate_v19.py', 'policy', '--policy-config', str(source),
                    '--work', str(directory / 'results'), '--jar', str(jar)]
            with patch.object(sys, 'argv', argv), patch.object(workflow, 'policy') as policy, \
                    patch.object(workflow, 'calibrate') as calibrate:
                workflow.main()
            calibrate.assert_not_called()
            policy.assert_called_once()
            args, actual_source = policy.call_args.args
            self.assertEqual(actual_source, source.resolve())
            self.assertEqual(args.iterations, 300)

    def test_resume_and_selected_config_handoff(self):
        with tempfile.TemporaryDirectory() as tmp:
            args = SimpleNamespace(work=Path(tmp), iterations=300, seed=4711,
                                   trials=2, jar=Path(tmp) / 'test.jar')
            args.jar.write_bytes(b'test')
            source = workflow.ROOT / 'input/v1.9/gunma-v1.9-config.xml'
            def stats(calibrator, trial, *unused):
                for mode in workflow.MODES:
                    trial.set_user_attr(f'{mode}_share', workflow.TARGET[mode])
                return [.1] * 4 if trial.number == 0 else [.2] * 4
            with patch.object(workflow, 'java_run') as run, patch.object(workflow.OverallCalibrator, 'calc_stats', stats):
                workflow.calibrate(args, 'overall', source)
                self.assertEqual(run.call_count, 2)
                workflow.calibrate(args, 'overall', source)
                self.assertEqual(run.call_count, 2)
            import json
            selection = json.loads((args.work / 'overall/selection.json').read_text())
            self.assertEqual(selection['trial'], 0)
            args.seed = 99
            with self.assertRaisesRegex(ValueError, 'inputs changed'):
                workflow.calibrate(args, 'overall', source)


if __name__ == '__main__':
    unittest.main()
