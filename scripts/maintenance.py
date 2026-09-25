#!/usr/bin/env python3
"""Offline backup/restore for this repository's single-instance Compose deployment."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import select
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
TABLES = sorted(['departments', 'roles', 'users', 'user_roles', 'counselors',
                 'counselor_status_history', 'legacy_counselor_mapping', 'system_accounts',
                 'account_admin_guard', 'audit_events', 'flyway_schema_history'])
FILE_NAME = re.compile(r'[A-Za-z0-9_-]+\.(?:png|jpg|jpeg)')
IMAGE_ID = re.compile(r'sha256:[0-9a-f]{64}')


class MaintenanceError(Exception):
    pass


def require(condition, message):
    if not condition:
        raise MaintenanceError(message)


def run(args, *, data=None, stdin=None, stdout=None, timeout=600):
    # Never echo command output: SQL errors may contain credentials or personal data.
    try:
        result = subprocess.run(args, input=data, stdin=stdin, stdout=stdout or subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=timeout, env=clean_env())
    except (OSError, subprocess.TimeoutExpired) as error:
        raise MaintenanceError('External command unavailable or timed out; application remains stopped.') from error
    require(result.returncode == 0, f'{args[0]} command failed (exit {result.returncode}); inspect local service state.')
    return result.stdout or b''


def clean_env():
    return {k: v for k, v in os.environ.items() if not k.startswith(('COMPOSE_', 'APP_', 'DB_', 'MYSQL_'))}


def digest_file(path):
    with path.open('rb') as stream:
        return digest_stream(stream)


def digest_stream(stream):
    digest = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        digest.update(block)
    return digest.hexdigest()


def archive_inventory(path):
    """Read only: never extract an archive on the host, reject links and traversal."""
    inventory = {}
    with tarfile.open(path, 'r:') as archive:
        for member in archive:
            if member.name in ('.', './') and member.isdir():
                continue
            name = member.name[2:] if member.name.startswith('./') else member.name
            require(FILE_NAME.fullmatch(name) is not None, 'Archive contains an invalid upload path.')
            require(member.type in (tarfile.REGTYPE, tarfile.AREGTYPE) and not member.sparse,
                    'Archive contains a link, directory, sparse file or special file.')
            require(name not in inventory, 'Archive contains duplicate upload names.')
            with archive.extractfile(member) as stream:
                inventory[name] = {'size': member.size, 'sha256': digest_stream(stream)}
    return inventory


def validate_bundle(folder):
    require(folder.is_dir() and not folder.is_symlink(), 'Backup directory missing or is a symlink.')
    require(set(p.name for p in folder.iterdir()) == {'manifest.json', 'database.sql', 'uploads.tar'},
            'Backup incomplete or contains unexpected files.')
    for name in ('manifest.json', 'database.sql', 'uploads.tar'):
        path = folder / name
        require(path.is_file() and not path.is_symlink(), 'Backup file missing, invalid or is a symlink.')
    require((folder / 'manifest.json').stat().st_size < 16 * 1024 * 1024, 'Manifest too large.')
    manifest = json.loads((folder / 'manifest.json').read_text())
    require(isinstance(manifest, dict), 'Invalid manifest object.')
    require(manifest.get('format') == 1 and manifest.get('database') == 'counselor', 'Unsupported backup format/database.')
    require(isinstance(manifest.get('source_project'), str) and
            re.fullmatch(r'[a-z0-9][a-z0-9_-]{0,62}', manifest['source_project']), 'Invalid source identity.')
    require(isinstance(manifest.get('rows_sha256'), str) and
            re.fullmatch(r'[0-9a-f]{64}', manifest['rows_sha256']), 'Missing database row digest.')
    require(isinstance(manifest.get('mysql_version'), str) and manifest['mysql_version'], 'Missing database version.')
    require(isinstance(manifest.get('counts'), dict) and set(manifest['counts']) == set(TABLES),
            'Unexpected table set in manifest.')
    require(isinstance(manifest.get('files'), dict) and isinstance(manifest.get('uploads'), dict),
            'Invalid file inventory.')
    require(all(type(value) is int and value >= 0 for value in manifest['counts'].values()), 'Invalid table counts.')
    history = manifest.get('flyway')
    require(isinstance(history, list) and len(history) == 4 and
            all(isinstance(row, str) and re.fullmatch(str(index) + r'\t-?\d+\t1', row)
                for index, row in enumerate(history, 1)), 'Invalid migration history.')
    require(isinstance(manifest.get('photo_references'), list), 'Invalid photo references.')
    for key in ('app_image', 'db_image'):
        require(isinstance(manifest.get(key), str) and IMAGE_ID.fullmatch(manifest[key]), 'Invalid image identity.')
    for name in ('database.sql', 'uploads.tar'):
        require(digest_file(folder / name) == manifest['files'][name]['sha256']
                and (folder / name).stat().st_size == manifest['files'][name]['size'], 'Backup checksum/size mismatch.')
    require((folder / 'database.sql').stat().st_size > 0, 'Empty SQL dump.')
    require(archive_inventory(folder / 'uploads.tar') == manifest['uploads'], 'Upload inventory mismatch.')
    for ref in manifest['photo_references']:
        require(isinstance(ref, str) and ref.startswith('/uploads/') and FILE_NAME.fullmatch(ref[9:]),
                'Invalid database photo reference.')
        require(ref[9:] in manifest['uploads'], 'A referenced photo is missing from the backup.')
    return manifest


class Deployment:
    def __init__(self, project, env_file):
        require(re.fullmatch(r'[a-z0-9][a-z0-9_-]{0,62}', project), 'Invalid explicit Compose project name.')
        self.project = project
        self.env_file = Path(env_file).absolute()
        require(self.env_file.is_file() and not self.env_file.is_symlink(), 'Environment file missing or is a symlink.')
        require(self.env_file.stat().st_mode & 0o077 == 0, 'Environment file must have permissions 600.')
        self.base = ['docker', 'compose', '--project-name', project, '--env-file', str(self.env_file),
                     '-f', str(ROOT / 'compose.yaml')]

    def compose(self, *args, **kwargs):
        try:
            return run(self.base + list(args), **kwargs)
        except MaintenanceError as error:
            raise MaintenanceError(f'Compose {args[0]} failed; application was not automatically restarted.') from error

    def inspect(self, service):
        ids = self.compose('ps', '--all', '--quiet', service).decode().split()
        require(len(ids) == 1, f'Expected exactly one {service} container.')
        info = json.loads(run(['docker', 'inspect', ids[0]]))[0]
        labels = info['Config']['Labels']
        require(labels.get('com.docker.compose.project') == self.project and
                labels.get('com.docker.compose.service') == service, 'Container ownership mismatch.')
        return info

    def volumes(self):
        result = {}
        for service, destination, key in [('db', '/var/lib/mysql', 'mysql-data'), ('app', '/app/uploads', 'uploads')]:
            container = self.inspect(service)
            mounts = [m for m in container['Mounts'] if m['Destination'] == destination]
            require(len(mounts) == 1 and mounts[0]['Type'] == 'volume', 'Only managed named volumes are supported.')
            name = mounts[0]['Name']
            labels = json.loads(run(['docker', 'volume', 'inspect', name]))[0].get('Labels') or {}
            require(labels.get('com.docker.compose.project') == self.project and
                    labels.get('com.docker.compose.volume') == key, 'Shared/external volumes are not supported.')
            consumers = run(['docker', 'ps', '-aq', '--no-trunc', '--filter', 'volume=' + name]).decode().split()
            require(consumers == [container['Id']], 'Volume is shared with another container.')
            result[key] = name
        return result

    def require_stopped(self):
        state = self.inspect('app')['State']
        require(state['Status'] in ('created', 'exited') and not state['Running'], 'Application must remain stopped.')

    def require_standard_connections(self, app, db):
        app_env = dict(entry.split('=', 1) for entry in app['Config']['Env'])
        db_env = dict(entry.split('=', 1) for entry in db['Config']['Env'])
        url = app_env.get('DB_URL', '')
        require(url.split('?', 1)[0] == 'jdbc:mysql://db:3306/counselor'
                and app_env.get('UPLOAD_DIR') == '/app/uploads'
                and app_env.get('DB_USERNAME') == db_env.get('MYSQL_USER') == 'counselor_app'
                and db_env.get('MYSQL_DATABASE') == 'counselor'
                and app_env.get('DB_PASSWORD') == db_env.get('MYSQL_PASSWORD'),
                'Application connections do not match the supported Compose database/upload volume.')
        require(not any(key.startswith('SPRING_') or key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS')
                        for key in app_env), 'Additional runtime connection/configuration overrides are not supported.')
        require(not app['Config'].get('Cmd') and
                '--spring.profiles.active=deploy' in (app['Config'].get('Entrypoint') or []),
                'Custom application startup arguments are not supported.')
        require(all(mount['Destination'] == '/app/uploads' or
                    (mount['Destination'] == '/tmp' and mount['Type'] == 'tmpfs') for mount in app['Mounts']),
                'Additional application mounts are not supported.')

    def sql_args(self, root=False):
        password = '$MYSQL_ROOT_PASSWORD' if root else '$MYSQL_PASSWORD'
        user = 'root' if root else '"$MYSQL_USER"'
        return self.base + ['exec', '-T', 'db', 'sh', '-c',
            f'MYSQL_PWD="{password}" exec mysql --default-character-set=utf8mb4 --protocol=TCP '
            f'-h127.0.0.1 -u{user} --batch --skip-column-names --unbuffered --skip-reconnect --binary-mode --local-infile=0 "$MYSQL_DATABASE"']

    def sql(self, query, root=False):
        return run(self.sql_args(root), data=(query + ';\n').encode()).decode().strip()

    def table_names(self):
        return self.sql('SELECT TABLE_NAME FROM information_schema.tables WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME').splitlines()

    def metadata(self):
        require(self.table_names() == TABLES, 'Database does not match the supported V1-V4 table set.')
        for catalog, field in [('triggers', 'TRIGGER_SCHEMA'), ('routines', 'ROUTINE_SCHEMA'), ('events', 'EVENT_SCHEMA')]:
            require(self.sql(f'SELECT COUNT(*) FROM information_schema.{catalog} WHERE {field}=DATABASE()') == '0',
                    'Stored programs, triggers and scheduled events are not supported.')
        require(self.sql("SELECT COUNT(*) FROM information_schema.tables WHERE TABLE_SCHEMA=DATABASE() "
                         "AND (TABLE_TYPE <> 'BASE TABLE' OR ENGINE <> 'InnoDB')") == '0',
                'Only InnoDB base tables are supported.')
        counts = {name: int(self.sql(f'SELECT COUNT(*) FROM `{name}`')) for name in TABLES}
        history = self.sql('SELECT version, COALESCE(checksum,0), success FROM flyway_schema_history ORDER BY installed_rank').splitlines()
        require([row.split('\t')[0] for row in history] == ['1', '2', '3', '4']
                and all(row.endswith('\t1') for row in history), 'Expected complete non-demo Flyway V1-V4 history.')
        require(self.sql("SELECT COUNT(*) FROM system_accounts WHERE role='ADMIN' AND enabled=1") != '0',
                'Backup requires an enabled administrator.')
        refs = self.sql("SELECT DISTINCT photo_path FROM counselors WHERE photo_path IS NOT NULL AND photo_path <> '' ORDER BY photo_path").splitlines()
        return {'counts': counts, 'flyway': history, 'photo_references': refs}

    def dump(self, path, rows_only=False):
        options = ('--single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF '
                   '--default-character-set=utf8mb4 --hex-blob --order-by-primary --skip-extended-insert '
                   '--skip-comments --skip-add-locks --skip-add-drop-table')
        if rows_only:
            options += ' --no-create-info --skip-triggers'
        with path.open('xb') as output:
            run(self.base + ['exec', '-T', 'db', 'sh', '-c',
                'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --protocol=TCP -h127.0.0.1 -u"$MYSQL_USER" '
                + options + ' "$MYSQL_DATABASE"'], stdout=output)

    def archive(self, path, volume, image):
        with path.open('xb') as output:
            run(volume_command(volume, image, readonly=True) + ['tar', '-C', '/data', '-cf', '-', '.'], stdout=output)

    def require_new_project(self):
        for kind, args in [('container', ['ps', '-aq']), ('volume', ['volume', 'ls', '-q']), ('network', ['network', 'ls', '-q'])]:
            ids = run(['docker'] + args + ['--filter', 'label=com.docker.compose.project=' + self.project]).strip()
            require(not ids, f'Restore refuses existing project {kind}s; choose a new project name.')
        # Also protect orphan resources which lost their Compose labels.
        names = run(['docker', 'volume', 'ls', '--format', '{{.Name}}']).decode().splitlines()
        for name in ('mysql-data', 'uploads'):
            require(self.project + '_' + name not in names, 'Restore refuses an existing target volume.')


def volume_command(volume, image, readonly):
    return ['docker', 'run', '--rm', '-i', '--network', 'none', '--read-only', '--user', '10001:10001',
            '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges:true', '--mount',
            f'type=volume,src={volume},dst=/data,volume-nocopy' + (',readonly' if readonly else ''),
            '--entrypoint', '/usr/bin/env', image]


@contextmanager
def database_read_lock(deployment):
    """Hold a dedicated server connection through BOTH exports; release even on errors."""
    process = subprocess.Popen(deployment.sql_args(root=True), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.DEVNULL, env=clean_env())
    connection = None
    def exchange(query):
        process.stdin.write((query + ';\n').encode())
        process.stdin.flush()
        require(select.select([process.stdout], [], [], 30)[0], 'Database maintenance lock timed out.')
        line = process.stdout.readline().decode().strip()
        require(line, 'Database maintenance lock connection closed.')
        return line
    try:
        connection = exchange('SELECT CONNECTION_ID()')
        require(connection.isdigit(), 'Invalid maintenance lock connection.')
        require(exchange("FLUSH TABLES WITH READ LOCK; SELECT 'locked'") == 'locked', 'Could not lock database writes.')
        yield
        require(process.poll() is None, 'Database lock connection lost during backup.')
        require(exchange("SELECT 'locked'") == 'locked', 'Database lock connection lost during backup.')
    finally:
        # Kill only the connection ID returned by this process, not any other DB client.
        if process.poll() is None:
            try:
                process.stdin.close()
                process.wait(timeout=5)
            except (OSError, subprocess.TimeoutExpired):
                if connection and connection.isdigit():
                    try:
                        deployment.sql('KILL CONNECTION ' + connection, root=True)
                    finally:
                        process.kill()
                        process.wait(timeout=5)
                else:
                    process.kill()
                    process.wait(timeout=5)
        if not process.stdin.closed:
            process.stdin.close()
        process.stdout.close()


@contextmanager
def project_lock(project):
    directory = ROOT / 'target' / 'maintenance-locks'
    directory.mkdir(parents=True, exist_ok=True)
    with (directory / (project + '.lock')).open('a') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise MaintenanceError('Another maintenance command is using this project.') from error
        yield


def backup(deployment, output):
    output = Path(output).absolute()
    require(not output.exists() and not output.is_symlink(), 'Backup output already exists; never overwrite it.')
    require(output.parent.is_dir(), 'Create the backup parent directory first.')
    app, db = deployment.inspect('app'), deployment.inspect('db')
    deployment.require_standard_connections(app, db)
    volumes = deployment.volumes()
    require(db['State']['Running'] and db['State'].get('Health', {}).get('Status') == 'healthy', 'Database must be healthy.')
    # Validate topology before changing application state.
    require(not db['HostConfig'].get('PortBindings'), 'Backup requires the private Compose database with no published port.')
    output.mkdir(mode=0o700)
    (output / '.incomplete').touch(mode=0o600)
    deployment.compose('stop', 'app')
    deployment.require_stopped()
    print('Application stopped; keep other operators and direct DB/file writers out of this maintenance window.', flush=True)
    with database_read_lock(deployment):
        metadata = deployment.metadata()
        deployment.dump(output / 'database.sql')
        deployment.archive(output / 'uploads.tar', volumes['uploads'], app['Image'])
        uploads = archive_inventory(output / 'uploads.tar')
        for ref in metadata['photo_references']:
            require(ref.startswith('/uploads/') and ref[9:] in uploads, 'Referenced photo missing; backup remains incomplete.')
        with tempfile.TemporaryDirectory(prefix='counselor-row-check-') as temporary:
            rows = Path(temporary) / 'rows.sql'
            deployment.dump(rows, rows_only=True)
            row_digest = digest_file(rows)
        require(metadata == deployment.metadata(), 'Database changed during backup.')
        deployment.require_stopped()
    manifest = dict(metadata, format=1, database='counselor', created_at=datetime.now(timezone.utc).isoformat(),
                    source_project=deployment.project, source_checkout_commit=run(['git', '-C', str(ROOT), 'rev-parse', 'HEAD']).decode().strip(),
                    app_image=app['Image'], db_image=db['Image'], mysql_version=deployment.sql('SELECT VERSION()'),
                    uploads=uploads, rows_sha256=row_digest,
                    files={name: {'size': (output / name).stat().st_size, 'sha256': digest_file(output / name)}
                           for name in ('database.sql', 'uploads.tar')})
    (output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    (output / '.incomplete').unlink()
    validate_bundle(output)
    print(f'Backup complete: {output}\nApplication remains stopped. Resume it explicitly after the maintenance window.', flush=True)


def restore(deployment, source):
    started = time.monotonic()
    source = Path(source).absolute()
    # Snapshot verified artifacts privately; never import a file that can change after validation.
    validate_bundle(source)
    with tempfile.TemporaryDirectory(prefix='counselor-restore-') as temporary:
        bundle = Path(temporary) / 'bundle'
        bundle.mkdir(mode=0o700)
        for name in ('manifest.json', 'database.sql', 'uploads.tar'):
            shutil.copyfile(source / name, bundle / name, follow_symlinks=False)
        manifest = validate_bundle(bundle)
        require(deployment.project != manifest['source_project'], 'Cannot restore over the source project.')
        deployment.require_new_project()
        for key in ('app_image', 'db_image'):
            run(['docker', 'image', 'inspect', manifest[key]])
        override = Path(temporary) / 'images.json'
        override.write_text(json.dumps({'services': {
            'app': {'image': manifest['app_image'], 'environment': {'APP_BOOTSTRAP_USERNAME': '', 'APP_BOOTSTRAP_PASSWORD': ''}},
            'db': {'image': manifest['db_image']}}}))
        original_base = list(deployment.base)
        deployment.base += ['-f', str(override)]
        try:
            deployment.compose('up', '-d', '--no-build', '--wait', '--wait-timeout', '180', 'db')
            deployment.compose('create', '--no-build', '--no-recreate', 'app')
            deployment.require_stopped()
            volumes = deployment.volumes()
            require(not deployment.table_names(), 'Target database is not empty; refusing import.')
            empty_tar = Path(temporary) / 'empty.tar'
            deployment.archive(empty_tar, volumes['uploads'], manifest['app_image'])
            require(not archive_inventory(empty_tar), 'Target upload volume is not empty; refusing import.')
            require(deployment.sql('SELECT VERSION()') == manifest['mysql_version'], 'MySQL version does not match backup.')
            with (bundle / 'database.sql').open('rb') as stream:
                run(deployment.sql_args(), stdin=stream)
            with (bundle / 'uploads.tar').open('rb') as stream:
                run(volume_command(volumes['uploads'], manifest['app_image'], readonly=False) +
                    ['sh', '-c', 'umask 077; exec tar -C /data --no-same-owner --no-same-permissions -xf -'], stdin=stream)
            metadata = deployment.metadata()
            require(all(metadata[key] == manifest[key] for key in metadata), 'Restored database metadata mismatch.')
            rows = Path(temporary) / 'restored-rows.sql'
            deployment.dump(rows, rows_only=True)
            require(digest_file(rows) == manifest['rows_sha256'], 'Restored row content hash mismatch.')
            actual_tar = Path(temporary) / 'restored-uploads.tar'
            deployment.archive(actual_tar, volumes['uploads'], manifest['app_image'])
            require(archive_inventory(actual_tar) == manifest['uploads'], 'Restored upload inventory mismatch.')
            deployment.require_stopped()
            print(f'Restore verified in {time.monotonic() - started:.1f}s. Project: {deployment.project}. '
                  'Application remains stopped; run docker compose start app, then verify login and images.', flush=True)
        finally:
            deployment.base = original_base


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('backup', 'restore', 'verify'))
    parser.add_argument('--project')
    parser.add_argument('--env-file')
    parser.add_argument('--output')
    parser.add_argument('--backup')
    args = parser.parse_args()
    os.umask(0o077)
    try:
        if args.operation == 'verify':
            require(args.backup, '--backup is required.')
            validate_bundle(Path(args.backup))
            print('Backup checksums, archive paths and photo references verified.')
        else:
            require(args.project and args.env_file, '--project and --env-file are required.')
            deployment = Deployment(args.project, args.env_file)
            with project_lock(deployment.project):
                if args.operation == 'backup':
                    require(args.output, '--output is required.')
                    backup(deployment, args.output)
                else:
                    require(args.backup, '--backup is required.')
                    restore(deployment, args.backup)
        return 0
    except (MaintenanceError, OSError, ValueError, KeyError, TypeError, tarfile.TarError) as error:
        message = str(error) if isinstance(error, MaintenanceError) else 'Invalid backup, filesystem or configuration data.'
        print('Maintenance failed: ' + message + ' No automatic restart or target deletion was performed.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
