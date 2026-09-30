import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('security', Path(__file__).parents[1] / 'check-security.py')
s = importlib.util.module_from_spec(spec)
spec.loader.exec_module(s)


class SecurityReportTests(unittest.TestCase):
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
