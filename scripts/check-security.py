#!/usr/bin/env python3
"""Scan packaged Java dependencies and both runtime images; fail closed on scan errors."""
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def findings(report, require_java=False):
    if not report.get('ArtifactName') or not isinstance(report.get('Results'), list) or not report['Results']:
        raise ValueError('Incomplete scanner report')
    if not any(r.get('Class') == 'os-pkgs' for r in report['Results']):
        raise ValueError('OS packages were not scanned')
    if require_java and not any(r.get('Class') == 'lang-pkgs' and r.get('Type') == 'jar' for r in report['Results']):
        raise ValueError('Packaged Java dependencies were not scanned')
    result = []
    for target in report['Results']:
        for item in target.get('Vulnerabilities') or []:
            result.append({key: item.get(key, '') for key in
                ('VulnerabilityID', 'PkgName', 'InstalledVersion', 'FixedVersion', 'Severity', 'PrimaryURL')} |
                {'target': target['Target'], 'class': target.get('Class', '')})
    return result


def main():
    output = ROOT / 'target' / 'security'
    output.mkdir(parents=True, exist_ok=True)
    scanner = ROOT / 'target' / 'tools' / 'trivy'
    scan_env = {k: v for k, v in os.environ.items() if not k.startswith('TRIVY_')}
    prefix = [str(scanner), '--config', os.devnull, '--cache-dir', str(ROOT / 'target' / 'trivy-cache')]
    mysql = re.search(r'image: (mysql:\S+)', (ROOT / 'compose.yaml').read_text()).group(1)
    summary = {'checked_at': datetime.now(timezone.utc).isoformat(), 'passed': False,
               'policy': 'Fail on every HIGH/CRITICAL finding, including unfixed; no suppressions.', 'images': {}}
    failed = False
    with tempfile.NamedTemporaryFile() as empty_ignore:
        for name, image in [('application', 'campus-counselor-management:local'), ('mysql', mysql)]:
            try:
                subprocess.run(prefix + ['image', '--scanners', 'vuln', '--ignorefile', empty_ignore.name,
                    '--severity', 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL', '--ignore-unfixed=false',
                    '--exit-code', '0', '--list-all-pkgs', '--format', 'json', '--output', str(output / (name + '.json')), '--timeout', '15m', image],
                    check=True, timeout=960, env=scan_env)
                report = json.loads((output / (name + '.json')).read_text())
                issues = findings(report, require_java=name == 'application')
                blocked = [i for i in issues if i['Severity'] in ('HIGH', 'CRITICAL')]
                summary['images'][name] = {'artifact': report['ArtifactName'], 'metadata': report.get('Metadata'),
                    'findings': len(issues), 'blocking': blocked}
                if name == 'application':
                    (output / 'java-dependencies.json').write_text(json.dumps(
                        [r for r in report['Results'] if r.get('Class') == 'lang-pkgs'], indent=2))
                failed |= bool(blocked)
            except (OSError, subprocess.SubprocessError, ValueError, KeyError):
                summary['images'][name] = {'error': 'Scan did not complete; inspect scanner logs.'}
                failed = True
    try:
        version = subprocess.check_output(prefix + ['version', '--format', 'json'], timeout=30, env=scan_env)
        summary['scanner'] = json.loads(version)
        if summary['scanner'].get('Version') != '0.74.0':
            failed = True
            summary['version_error'] = 'Unexpected scanner version'
    except (OSError, subprocess.SubprocessError, ValueError):
        failed = True
        summary['version_error'] = 'Scanner/database metadata unavailable'
    summary['passed'] = not failed
    (output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print('Security gate: ' + ('PASS' if not failed else 'FAIL (findings or incomplete scan)'))
    return int(failed)


if __name__ == '__main__':
    raise SystemExit(main())
