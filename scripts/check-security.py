#!/usr/bin/env python3
"""Scan packaged Java dependencies and both runtime images; fail closed on scan errors."""
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def findings(report, require_java=False, require_gosu=False):
    if not report.get('ArtifactName') or not isinstance(report.get('Results'), list) or not report['Results']:
        raise ValueError('Incomplete scanner report')
    if not any(r.get('Class') == 'os-pkgs' for r in report['Results']):
        raise ValueError('OS packages were not scanned')
    if require_java and not any(r.get('Class') == 'lang-pkgs' and r.get('Type') == 'jar' for r in report['Results']):
        raise ValueError('Packaged Java dependencies were not scanned')
    if require_gosu and not any(r.get('Type') == 'gobinary' and r.get('Target', '').lstrip('/') == 'usr/local/bin/gosu'
            and any(p.get('Name') == 'stdlib' for p in r.get('Packages', [])) for r in report['Results']):
        raise ValueError('gosu and its Go standard library were not scanned')
    result = []
    for target in report['Results']:
        for item in target.get('Vulnerabilities') or []:
            result.append({key: item.get(key, '') for key in
                ('VulnerabilityID', 'PkgName', 'InstalledVersion', 'FixedVersion', 'Severity', 'PrimaryURL')} |
                {'target': target['Target'], 'class': target.get('Class', '')})
    return result


def runtime_images():
    # Resolve the actual Compose services without reading a private .env file or host overrides.
    env = {k: v for k, v in os.environ.items() if not k.startswith(('COMPOSE_', 'APP_', 'DB_', 'MYSQL_'))}
    env.update(DB_PASSWORD='scan-config-only', MYSQL_ROOT_PASSWORD='scan-config-only')
    raw = subprocess.check_output(['docker', 'compose', '--project-name', 'counselor-security-config',
        '--env-file', os.devnull, '-f', str(ROOT / 'compose.yaml'), 'config', '--format', 'json'], timeout=30, env=env)
    services = json.loads(raw)['services']
    images = [(name, services[service]['image']) for name, service in [('application', 'app'), ('mysql', 'db')]]
    if any(not isinstance(image, str) or not image.strip() for _, image in images):
        raise ValueError('Missing runtime image')
    return images


def main():
    output = ROOT / 'target' / 'security'
    output.mkdir(parents=True, exist_ok=True)
    scanner = ROOT / 'target' / 'tools' / 'trivy'
    scan_env = {k: v for k, v in os.environ.items() if not k.startswith('TRIVY_')}
    prefix = [str(scanner), '--config', os.devnull, '--cache-dir', str(ROOT / 'target' / 'trivy-cache')]
    summary = {'checked_at': datetime.now(timezone.utc).isoformat(), 'passed': False,
               'policy': 'Fail on every HIGH/CRITICAL finding, including unfixed; no suppressions.', 'images': {}}
    failed = False
    try:
        images = runtime_images()
    except (OSError, subprocess.SubprocessError, ValueError, KeyError, TypeError):
        summary['configuration_error'] = 'Runtime image configuration unavailable.'
        (output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
        return 1
    with tempfile.NamedTemporaryFile() as empty_ignore:
        for name, image in images:
            try:
                image_id = subprocess.check_output(['docker', 'image', 'inspect', '--format', '{{.Id}}', image], timeout=30).decode().strip()
                if not image_id.startswith('sha256:'):
                    raise ValueError('Runtime image not built')
                subprocess.run(prefix + ['image', '--image-src', 'docker', '--scanners', 'vuln', '--ignorefile', empty_ignore.name,
                    '--severity', 'UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL', '--ignore-unfixed=false',
                    '--exit-code', '0', '--list-all-pkgs', '--format', 'json', '--output', str(output / (name + '.json')), '--timeout', '15m', image_id],
                    check=True, timeout=960, env=scan_env)
                report = json.loads((output / (name + '.json')).read_text())
                if report.get('Metadata', {}).get('ImageID') != image_id:
                    raise ValueError('Scanned image identity differs from runtime image')
                issues = findings(report, require_java=name == 'application', require_gosu=name == 'mysql')
                blocked = [i for i in issues if i['Severity'] in ('HIGH', 'CRITICAL')]
                summary['images'][name] = {'configured_image': image, 'image_id': image_id, 'artifact': report['ArtifactName'], 'metadata': report.get('Metadata'),
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
