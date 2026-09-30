#!/usr/bin/env python3
"""Run actual packaged migrations in fresh private Compose databases; no host DB ports."""
import json
import os
from pathlib import Path
import secrets
import signal
import tempfile
import maintenance as m


def main():
    os.umask(0o077)
    name = 'counselor-migration-' + secrets.token_hex(6)
    report = m.ROOT / 'target' / 'migration-verification' / name
    report.mkdir(parents=True)
    checks = []
    def interrupted(signum, frame):
        raise SystemExit(128 + signum)
    signal.signal(signal.SIGTERM, interrupted)
    with tempfile.TemporaryDirectory(prefix=name) as temporary:
        env = Path(temporary) / '.env'
        password = secrets.token_urlsafe(24)
        env.write_text(f'DB_PASSWORD={password}\nMYSQL_ROOT_PASSWORD={secrets.token_urlsafe(24)}\n')
        try:
            for scenario in ('fresh', 'missing-mapping', 'mapped-legacy', 'v3-upgrade', 'unversioned'):
                deployment = m.Deployment(name + '-' + scenario, env)
                deployment.require_new_project()
                try:
                    deployment.compose('up', '-d', '--wait', '--wait-timeout', '180', 'db')
                    output = m.run(['docker', 'run', '--rm', '--name', deployment.project + '-runner',
                        '--label', 'com.docker.compose.project=' + deployment.project,
                        '--network', deployment.project + '_default', '--env-file', str(env),
                        '--read-only', '--tmpfs', '/tmp:rw,noexec,nosuid', '--cap-drop', 'ALL',
                        '--security-opt', 'no-new-privileges:true', 'campus-counselor-migrations:local', scenario])
                    text = output.decode().replace(password, '[REDACTED]')
                    (report / (scenario + '.log')).write_text(text)
                    assert 'PASS ' + scenario in text
                    checks.append(scenario)
                    print('PASS ' + scenario, flush=True)
                finally:
                    # This name is fresh and owned by this run; interruption may leave docker run alive.
                    try:
                        if m.run(['docker', 'ps', '-aq', '--filter',
                                  'name=^/' + deployment.project + '-runner$']).strip():
                            m.run(['docker', 'rm', '-f', deployment.project + '-runner'])
                    finally:
                        deployment.compose('down', '--volumes', '--remove-orphans')
        finally:
            (report / 'result.json').write_text(json.dumps({'passed': len(checks) == 5, 'checks': checks,
                'project_prefix': name, 'commit': m.run(['git', '-C', str(m.ROOT), 'rev-parse', 'HEAD']).decode().strip()}, indent=2))
            print('Evidence: ' + str(report), flush=True)


if __name__ == '__main__':
    main()
