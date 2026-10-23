"""Regression checks for the standalone parallel policy experiment."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import threading
from concurrent.futures import ThreadPoolExecutor
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
EXPERIMENT = ROOT / 'experiments/2026-10-23d-oldagebasedparams-base-policy-parallel'
sys.path.insert(0, str(ROOT / 'src/main/python'))
spec = importlib.util.spec_from_file_location('parallel_policy', EXPERIMENT / 'src/main/python/run_parallel_policy.py')
parallel = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parallel)


class ParallelPolicyTest(unittest.TestCase):
    def test_concurrent_cases_resume_and_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            def args(case):
                return SimpleNamespace(case=case, work=directory / 'results', jar=directory / 'test.jar',
                    config=EXPERIMENT / 'oldage-config.xml', seed=4711, iterations=300, jvm_args='')
            barrier = threading.Barrier(2)
            def simulate(args, config, output, switch):
                barrier.wait(timeout=5)
                output.mkdir()
            with patch.object(parallel.workflow, 'signature', return_value={'seed': 4711}), \
                    patch.object(parallel.workflow, 'java_run', side_effect=simulate) as run, \
                    patch.object(parallel.workflow, 'policy_report') as report:
                with ThreadPoolExecutor(2) as executor:
                    jobs = [executor.submit(parallel.run, args(case)) for case in ['baseline', 'policy']]
                    for job in jobs:
                        job.result()
                self.assertEqual(run.call_count, 2)
                parallel.run(args('baseline'))
                parallel.run(args('policy'))
                self.assertEqual(run.call_count, 2)
                parallel.run(args('report'))
                report.assert_called_once()
                (directory / 'results/comparison/policy.complete').unlink()
                with self.assertRaisesRegex(ValueError, 'not completed'):
                    parallel.run(args('report'))
            with patch.object(parallel.workflow, 'signature', return_value={'seed': 99}):
                with self.assertRaisesRegex(ValueError, 'inputs changed'):
                    parallel.run(args('baseline'))

    def test_failure_has_no_completion_marker(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            args = SimpleNamespace(case='baseline', work=directory / 'results', jar=directory / 'test.jar',
                config=EXPERIMENT / 'oldage-config.xml', seed=4711, iterations=300, jvm_args='')
            with patch.object(parallel.workflow, 'signature', return_value={'seed': 4711}), \
                    patch.object(parallel.workflow, 'java_run', side_effect=RuntimeError('simulation failed')):
                with self.assertRaisesRegex(RuntimeError, 'simulation failed'):
                    parallel.run(args)
            self.assertFalse((args.work / 'comparison/baseline.complete').exists())


if __name__ == '__main__':
    unittest.main()
