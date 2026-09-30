import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

spec = importlib.util.spec_from_file_location('ci_runner', Path(__file__).parents[1] / 'verify-mysql-ci.py')
r = importlib.util.module_from_spec(spec)
spec.loader.exec_module(r)


class CiRunnerTests(unittest.TestCase):
    def test_failed_suite_does_not_hide_other_suites(self):
        children = [Mock(), Mock(), Mock()]
        for child, code in zip(children, [1, 0, 0]):
            child.wait.return_value = code
            child.poll.return_value = code
        with tempfile.TemporaryDirectory() as temporary, patch.object(r, 'ROOT', Path(temporary)), \
                patch.object(r.subprocess, 'Popen', side_effect=children) as spawn, patch.object(r.signal, 'signal'):
            self.assertEqual(r.main(), 1)
            report = json.loads((Path(temporary) / 'target/mysql-ci-summary.json').read_text())
            self.assertFalse(report['passed'])
            self.assertEqual(len(report['suites']), 3)
            self.assertEqual(spawn.call_count, 3)

    def test_timeout_terminates_suite_and_records_failure(self):
        child = Mock()
        child.wait.side_effect = [subprocess.TimeoutExpired('test', 900), -15]
        child.poll.return_value = -15
        with tempfile.TemporaryDirectory() as temporary, patch.object(r, 'ROOT', Path(temporary)), \
                patch.object(r, 'SUITES', ('test',)), patch.object(r.subprocess, 'Popen', return_value=child), \
                patch.object(r.signal, 'signal'):
            self.assertEqual(r.main(), 1)
            child.terminate.assert_called_once()
            report = json.loads((Path(temporary) / 'target/mysql-ci-summary.json').read_text())
            self.assertEqual(report['suites'][0]['exit_code'], 124)
