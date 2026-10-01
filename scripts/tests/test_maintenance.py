import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('maintenance', Path(__file__).parents[1] / 'maintenance.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class BackupSafetyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.folder = self.root / 'backup'
        self.folder.mkdir()
        (self.folder / 'database.sql').write_bytes(b'CREATE TABLE sample(id INT);\n')
        self.archive([('avatar.png', tarfile.REGTYPE), ('orphan.jpg', tarfile.REGTYPE)])
        self.manifest = {
            'format': 1, 'database': 'counselor', 'app_image': 'sha256:' + 'a' * 64,
            'db_image': 'sha256:' + 'b' * 64, 'counts': dict.fromkeys(m.TABLES, 0),
            'source_project': 'source-test', 'rows_sha256': 'c' * 64, 'mysql_version': '8.4.11',
            'flyway': [f'{version}\t1\t1' for version in range(1, 5)],
            'photo_references': ['/uploads/avatar.png'], 'uploads': m.archive_inventory(self.folder / 'uploads.tar')}
        self.save_manifest()

    def archive(self, members):
        with tarfile.open(self.folder / 'uploads.tar', 'w') as archive:
            for name, kind in members:
                entry = tarfile.TarInfo(name)
                entry.type = kind
                entry.linkname = '/etc/passwd' if kind != tarfile.REGTYPE else ''
                entry.size = 4 if kind == tarfile.REGTYPE else 0
                archive.addfile(entry, io.BytesIO(b'data') if entry.size else None)

    def save_manifest(self):
        self.manifest['files'] = {n: {'size': (self.folder / n).stat().st_size, 'sha256': m.digest_file(self.folder / n)}
                                  for n in ('database.sql', 'uploads.tar')}
        (self.folder / 'manifest.json').write_text(json.dumps(self.manifest))

    def test_valid_bundle_preserves_orphans_and_checks_references(self):
        result = m.validate_bundle(self.folder)
        self.assertEqual(set(result['uploads']), {'avatar.png', 'orphan.jpg'})

    def test_modified_or_missing_sql_is_rejected(self):
        (self.folder / 'database.sql').write_bytes(b'changed')
        with self.assertRaises(m.MaintenanceError):
            m.validate_bundle(self.folder)
        (self.folder / 'database.sql').unlink()
        with self.assertRaises(m.MaintenanceError):
            m.validate_bundle(self.folder)

    def test_partial_backup_is_rejected(self):
        (self.folder / '.incomplete').touch()
        with self.assertRaises(m.MaintenanceError):
            m.validate_bundle(self.folder)

    def test_missing_referenced_image_is_rejected(self):
        self.manifest['photo_references'] = ['/uploads/missing.png']
        self.save_manifest()
        with self.assertRaises(m.MaintenanceError):
            m.validate_bundle(self.folder)

    def test_tampered_inventory_is_rejected(self):
        self.manifest['uploads']['avatar.png']['sha256'] = '0' * 64
        self.save_manifest()
        with self.assertRaises(m.MaintenanceError):
            m.validate_bundle(self.folder)

    def test_archive_traversal_links_duplicates_and_special_files_are_rejected(self):
        for members in [[('../escape.png', tarfile.REGTYPE)], [('/absolute.png', tarfile.REGTYPE)],
                        [('nested/photo.png', tarfile.REGTYPE)], [('link.png', tarfile.SYMTYPE)],
                        [('link.png', tarfile.LNKTYPE)], [('pipe.png', tarfile.FIFOTYPE)],
                        [('same.png', tarfile.REGTYPE), ('same.png', tarfile.REGTYPE)]]:
            with self.subTest(members=members):
                self.archive(members)
                with self.assertRaises(m.MaintenanceError):
                    m.archive_inventory(self.folder / 'uploads.tar')
        self.assertFalse((self.root / 'escape.png').exists())

    def test_symlink_artifact_is_rejected(self):
        sql = self.folder / 'database.sql'
        sql.rename(self.root / 'real.sql')
        sql.symlink_to(self.root / 'real.sql')
        with self.assertRaises(m.MaintenanceError):
            m.validate_bundle(self.folder)

    def test_invalid_backup_fails_before_any_docker_action(self):
        (self.folder / 'uploads.tar').write_bytes(b'corrupt')
        with patch.object(m, 'run') as command:
            with self.assertRaises(m.MaintenanceError):
                m.restore(None, self.folder)
            command.assert_not_called()

    def test_existing_backup_output_is_never_overwritten(self):
        original = (self.folder / 'database.sql').read_bytes()
        with self.assertRaises(m.MaintenanceError):
            m.backup(None, self.folder)
        self.assertEqual((self.folder / 'database.sql').read_bytes(), original)

    def test_missing_row_digest_or_migration_history_is_rejected(self):
        for key in ('rows_sha256', 'flyway'):
            with self.subTest(key=key):
                value = self.manifest.pop(key)
                self.save_manifest()
                with self.assertRaises(m.MaintenanceError):
                    m.validate_bundle(self.folder)
                self.manifest[key] = value

    def test_source_with_different_database_or_upload_path_is_rejected(self):
        env = self.root / 'env'
        env.touch(mode=0o600)
        deployment = m.Deployment('source-test', env)
        app = {'Config': {'Env': ['DB_URL=jdbc:mysql://db:3306/counselor', 'UPLOAD_DIR=/app/uploads',
                                  'DB_USERNAME=counselor_app', 'DB_PASSWORD=test-only'],
                          'Cmd': None, 'Entrypoint': ['java', '--spring.profiles.active=deploy']}, 'Mounts': []}
        db = {'Config': {'Env': ['MYSQL_DATABASE=counselor', 'MYSQL_USER=counselor_app', 'MYSQL_PASSWORD=test-only']}}
        deployment.require_standard_connections(app, db)
        for override in ('DB_URL=jdbc:mysql://another-host:3306/counselor', 'UPLOAD_DIR=/somewhere-else',
                         'SPRING_DATASOURCE_URL=jdbc:mysql://another-host:3306/counselor'):
            with self.subTest(override=override):
                app['Config']['Env'].append(override)
                with self.assertRaises(m.MaintenanceError):
                    deployment.require_standard_connections(app, db)
                app['Config']['Env'].pop()

    def test_existing_project_and_orphan_volume_are_rejected(self):
        env = self.root / 'env'
        env.touch(mode=0o600)
        deployment = m.Deployment('restore-test', env)
        for outputs in [[b'existing'], [b'', b'existing'], [b'', b'', b'existing'],
                        [b'', b'', b'', b'restore-test_uploads\n']]:
            with self.subTest(outputs=outputs), patch.object(m, 'run', side_effect=outputs):
                with self.assertRaises(m.MaintenanceError):
                    deployment.require_new_project()

    def test_env_file_permissions_and_project_names_are_checked(self):
        env = self.root / 'env'
        env.touch()
        env.chmod(0o644)
        with self.assertRaises(m.MaintenanceError):
            m.Deployment('restore-test', env)
        env.chmod(0o600)
        with self.assertRaises(m.MaintenanceError):
            m.Deployment('../another-project', env)


class HealthWaitTests(unittest.TestCase):
    def setUp(self):
        self.deployment = m.Deployment.__new__(m.Deployment)

    def test_starting_is_not_ready_until_healthy(self):
        states = [{'State': {'Status': 'running', 'Running': True, 'Health': {'Status': value}}}
                  for value in ('starting', 'healthy')]
        with patch.object(self.deployment, 'inspect', side_effect=states) as inspect, patch.object(m.time, 'sleep'):
            self.deployment.wait_healthy('app')
            self.assertEqual(inspect.call_count, 2)

    def test_stopped_unhealthy_and_missing_probe_are_rejected(self):
        for status, health in [('exited', 'starting'), ('dead', 'healthy'), ('restarting', 'starting'),
                              ('running', 'unhealthy'), ('running', None)]:
            state = {'State': {'Status': status, 'Running': status == 'running', 'Health': {'Status': health}}}
            with self.subTest(status=status, health=health), \
                    patch.object(self.deployment, 'inspect', return_value=state), self.assertRaises(m.MaintenanceError):
                self.deployment.wait_healthy('app')

    def test_stalled_startup_has_bounded_wait(self):
        state = {'State': {'Status': 'running', 'Running': True, 'Health': {'Status': 'starting'}}}
        with patch.object(self.deployment, 'inspect', return_value=state), \
                patch.object(m.time, 'monotonic', side_effect=[0, 0, 2]), patch.object(m.time, 'sleep'), \
                self.assertRaisesRegex(m.MaintenanceError, 'within 1s'):
            self.deployment.wait_healthy('app', timeout=1)


if __name__ == '__main__':
    unittest.main()
