#!/usr/bin/env python3
"""Run actual packaged migrations in fresh private Compose databases; no host DB ports."""
import json
import os
from pathlib import Path
import secrets
import re
import signal
import tempfile
import maintenance as m


def upgrade_fixture(deployment, temporary):
    # Only the disposable project's database exists; never reconfigure an existing deployment.
    deployment.sql("INSERT INTO departments(id,name) VALUES (7,'合成升级院系')")
    deployment.sql("INSERT INTO counselors(id,employee_no,name,department_id,employment_status,remark) "
                   "VALUES (42,'UPGRADE-042','合成升级档案',7,'ACTIVE','旧镜像生成的测试资料')")
    deployment.sql("INSERT INTO system_accounts(username,password_hash,role,enabled,counselor_id) "
                   "VALUES ('upgrade_fixture','synthetic-hash-not-used-for-login','ADMIN',1,42)")
    deployment.sql("INSERT INTO counselor_status_history(counselor_id,to_status,actor) VALUES (42,'ACTIVE','upgrade_fixture')")
    deployment.sql("INSERT INTO audit_events(actor,action,target_type,target_id,outcome,reason) "
                   "VALUES ('upgrade_fixture','COUNSELOR_CREATE','COUNSELOR','42','SUCCESS','OK')")
    before = deployment.metadata()
    original = Path(temporary) / 'before-upgrade.sql'
    deployment.dump(original, rows_only=True)
    old_image = deployment.inspect('db')['Image']
    deployment.compose('stop', 'db')
    # Remove the test-only official-image override, retaining the same named volume.
    deployment.base = deployment.base[:-2]
    deployment.compose('up', '-d', '--no-build', '--force-recreate', '--wait', '--wait-timeout', '180', 'db')
    current = deployment.inspect('db')
    assert current['Image'] != old_image
    assert current['Config']['Labels']['io.counselor.mysql.variant'] == '8.4.11-runtime-2'
    assert deployment.metadata() == before
    upgraded = Path(temporary) / 'after-upgrade.sql'
    deployment.dump(upgraded, rows_only=True)
    assert m.digest_file(original) == m.digest_file(upgraded)
    deployment.sql("UPDATE counselors SET remark='升级后写入',version=version+1 WHERE id=42 AND version=0")
    assert deployment.sql('SELECT version FROM counselors WHERE id=42') == '1'


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
            for scenario in ('fresh', 'missing-mapping', 'mapped-legacy', 'v3-upgrade', 'unversioned', 'runtime-upgrade'):
                deployment = m.Deployment(name + '-' + scenario, env)
                deployment.require_new_project()
                harness_scenario = 'fresh' if scenario == 'runtime-upgrade' else scenario
                if scenario == 'runtime-upgrade':
                    base_image = re.search(r'^FROM (mysql:\S+)$', (m.ROOT / 'docker/mysql/Dockerfile').read_text(), re.MULTILINE).group(1)
                    override = Path(temporary) / 'official-image.json'
                    override.write_text(json.dumps({'services': {'db': {'image': base_image}}}))
                    deployment.base += ['-f', str(override)]
                try:
                    deployment.compose('up', '-d', '--wait', '--wait-timeout', '180', 'db')
                    output = m.run(['docker', 'run', '--rm', '--name', deployment.project + '-runner',
                        '--label', 'com.docker.compose.project=' + deployment.project,
                        '--network', deployment.project + '_default', '--env-file', str(env),
                        '--read-only', '--tmpfs', '/tmp:rw,noexec,nosuid', '--cap-drop', 'ALL',
                        '--security-opt', 'no-new-privileges:true', 'campus-counselor-migrations:local', harness_scenario])
                    text = output.decode().replace(password, '[REDACTED]')
                    (report / (scenario + '.log')).write_text(text)
                    assert 'PASS ' + harness_scenario in text
                    if scenario == 'runtime-upgrade':
                        upgrade_fixture(deployment, temporary)
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
            (report / 'result.json').write_text(json.dumps({'passed': len(checks) == 6, 'checks': checks,
                'project_prefix': name, 'commit': m.run(['git', '-C', str(m.ROOT), 'rev-parse', 'HEAD']).decode().strip()}, indent=2))
            print('Evidence: ' + str(report), flush=True)


if __name__ == '__main__':
    main()
