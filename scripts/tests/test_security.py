import importlib.util
from pathlib import Path
import unittest
import json
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('security', Path(__file__).parents[1] / 'check-security.py')
s = importlib.util.module_from_spec(spec)
spec.loader.exec_module(s)


class SecurityReportTests(unittest.TestCase):
    def test_mysql_requires_scanned_gosu_standard_library_even_when_no_findings(self):
        report = {'ArtifactName': 'db', 'Results': [{'Class': 'os-pkgs', 'Target': 'oracle'}]}
        with self.assertRaises(ValueError):
            s.findings(report, require_gosu=True)
        go = {'Class': 'lang-pkgs', 'Type': 'gobinary', 'Target': 'usr/local/bin/gosu', 'Packages': []}
        report['Results'].append(go)
        with self.assertRaises(ValueError):
            s.findings(report, require_gosu=True)
        go['Packages'] = [{'Name': 'stdlib', 'Version': 'v1.27.1'}]
        self.assertEqual(s.findings(report, require_gosu=True), [])

    def test_incomplete_scan_is_not_a_clean_scan(self):
        for report in ({}, {'ArtifactName': 'app', 'Results': []},
                       {'ArtifactName': 'app', 'Results': [{'Class': 'lang-pkgs'}]}):
            with self.subTest(report=report), self.assertRaises(ValueError):
                s.findings(report)

    def test_application_requires_java_and_os_coverage(self):
        report = {'ArtifactName': 'app', 'Results': [{'Class': 'os-pkgs', 'Target': 'ubuntu'}]}
        with self.assertRaises(ValueError):
            s.findings(report, require_java=True)
        report['Results'].append({'Class': 'lang-pkgs', 'Type': 'jar', 'Target': 'app.jar'})
        self.assertEqual(s.findings(report, require_java=True), [])

    def test_unfixed_high_findings_are_retained(self):
        issue = {'VulnerabilityID': 'test-high', 'PkgName': 'sample', 'InstalledVersion': '1', 'Severity': 'HIGH'}
        report = {'ArtifactName': 'app', 'Results': [{'Class': 'os-pkgs', 'Target': 'ubuntu', 'Vulnerabilities': [issue]}]}
        result = s.findings(report)
        self.assertEqual(result[0]['Severity'], 'HIGH')
        self.assertEqual(result[0]['FixedVersion'], '')


class RuntimeImageSelectionTests(unittest.TestCase):
    def test_scans_configured_derived_images_without_private_environment_overrides(self):
        payload = {'services': {'app': {'image': 'app:tested'}, 'db': {'image': 'derived-db:tested'}}}
        with patch.dict(s.os.environ, {'COMPOSE_FILE': '/private/other.yaml', 'DB_PASSWORD': 'private-value'}), \
                patch.object(s.subprocess, 'check_output', return_value=json.dumps(payload).encode()) as command:
            self.assertEqual(s.runtime_images(), [('application', 'app:tested'), ('mysql', 'derived-db:tested')])
        env = command.call_args.kwargs['env']
        self.assertNotIn('COMPOSE_FILE', env)
        self.assertEqual(env['DB_PASSWORD'], 'scan-config-only')
        args = command.call_args.args[0]
        self.assertEqual(args[args.index('--env-file') + 1], s.os.devnull)

    def test_missing_database_image_cannot_silently_skip_database_scan(self):
        for db in ({}, {'image': ''}, {'image': None}):
            with patch.object(s.subprocess, 'check_output', return_value=json.dumps(
                    {'services': {'app': {'image': 'app:tested'}, 'db': db}}).encode()):
                with self.assertRaises((ValueError, KeyError)):
                    s.runtime_images()
