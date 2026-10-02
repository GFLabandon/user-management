#!/usr/bin/env python3
"""Synthetic MySQL query baseline in a newly-created private project; never targets an existing DB."""
import json
import os
from pathlib import Path
import platform
import secrets
import signal
import tempfile
from datetime import datetime, timezone
import maintenance as m


def main():
    os.umask(0o077)
    project = 'counselor-baseline-' + secrets.token_hex(6)
    report = m.ROOT / 'target' / 'query-baseline' / project
    report.mkdir(parents=True)
    result = {'passed': False, 'project': project, 'at': datetime.now(timezone.utc).isoformat()}
    result['sourceCommit'] = m.run(['git', '-C', str(m.ROOT), 'rev-parse', 'HEAD']).decode().strip()
    result['dirtyWorktree'] = bool(m.run(['git', '-C', str(m.ROOT), 'status', '--porcelain']).strip())
    result['docker'] = json.loads(m.run(['docker', 'info', '--format', '{{json .}}']))
    # Keep only machine capacity/version; omit Docker identifiers, paths and registry configuration.
    result['docker'] = {k: result['docker'][k] for k in ('NCPU', 'MemTotal', 'Architecture', 'ServerVersion')}
    result['host'] = {'system': platform.system(), 'machine': platform.machine()}
    if platform.system() == 'Darwin':
        result['host']['cpu'] = m.run(['sysctl', '-n', 'machdep.cpu.brand_string']).decode().strip()
        result['host']['memoryBytes'] = int(m.run(['sysctl', '-n', 'hw.memsize']))
    image = json.loads(m.run(['docker', 'image', 'inspect', 'campus-counselor-queries:local']))[0]['Id']
    result['harnessImage'] = image
    def interrupted(signum, frame):
        raise SystemExit(128 + signum)
    signal.signal(signal.SIGTERM, interrupted)
    with tempfile.TemporaryDirectory(prefix=project) as temporary:
        env = Path(temporary) / '.env'
        env.write_text(f'DB_PASSWORD={secrets.token_urlsafe(24)}\nMYSQL_ROOT_PASSWORD={secrets.token_urlsafe(24)}\n')
        deployment = m.Deployment(project, env)
        deployment.require_new_project()
        try:
            deployment.compose('up', '-d', '--wait', '--wait-timeout', '180', 'db')
            result['mysqlImage'] = deployment.inspect('db')['Image']
            output = m.run(['docker', 'run', '--rm', '--name', project + '-runner',
                            '--label', 'com.docker.compose.project=' + project,
                            '--network', project + '_default', '--env-file', str(env),
                            '--read-only', '--tmpfs', '/tmp:rw,noexec,nosuid', '--cap-drop', 'ALL',
                            '--security-opt', 'no-new-privileges:true', '--memory', '512m', image], timeout=600).decode()
            payloads = [line.removeprefix('BASELINE_JSON=') for line in output.splitlines() if line.startswith('BASELINE_JSON=')]
            m.require(len(payloads) == 1, 'Missing baseline result.')
            result.update(json.loads(payloads[0]))
            m.require(result['passed'] and len(result['results']) == 33, 'Incomplete query baseline.')
            for row in result['results']:
                print(f"PASS {row['counselors']:5d}/{row['auditEvents']:6d} {row['scenario']:30s} "
                      f"median={row['medianMs']:.2f}ms p95={row['p95Ms']:.2f}ms sql={row['sqlPerSearch']}", flush=True)
        finally:
            try:
                if m.run(['docker', 'ps', '-aq', '--filter', 'name=^/' + project + '-runner$']).strip():
                    m.run(['docker', 'rm', '-f', project + '-runner'])
                deployment.compose('down', '--volumes', '--remove-orphans')
                result['resourcesCleaned'] = True
            finally:
                (report / 'result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
                print('Evidence: ' + str(report), flush=True)


if __name__ == '__main__':
    main()
