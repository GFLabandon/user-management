import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock, patch
from contextlib import nullcontext

sys.path.insert(0, str(Path(__file__).parents[1]))
spec = importlib.util.spec_from_file_location('cleanup_images', Path(__file__).parents[1] / 'cleanup-images.py')
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)


class ImageCleanupSafetyTests(unittest.TestCase):
    def setUp(self):
        self.deployment = Mock(project='fixture-project')
        self.deployment.inspect.side_effect = lambda service: {'Image': 'sha256:fixture', 'State': {'Running': True, 'Health': {'Status': 'healthy'}}, 'HostConfig': {}}
        self.deployment.volumes.return_value = {'uploads': 'owned-volume'}
        self.deployment.metadata.return_value = {'photo_references': ['/uploads/active.png']}
        self.deployment.sql.return_value = ''
        self.inventory = {'active.png': {'size': 1, 'sha256': 'a' * 64}, 'orphan.png': {'size': 2, 'sha256': 'b' * 64}}
        self.manifest = {'source_project': 'fixture-project', 'uploads': self.inventory.copy()}
        for name, value in [('database_read_lock', nullcontext()), ('archive_inventory', self.inventory), ('validate_bundle', self.manifest)]:
            self.addCleanup(patch.stopall)
            patch.object(c, name, return_value=value).start()
        self.command = patch.object(c, 'run').start()

    def test_default_is_preview_and_never_invokes_deletion(self):
        result = c.cleanup(self.deployment)
        self.assertEqual(result['candidates'], ['orphan.png'])
        self.assertFalse(result['applied'])
        self.command.assert_not_called()

    def test_invalid_filename_and_missing_backup_fail_before_deployment_access(self):
        for name in ['../escape.png', '/uploads/file.png', 'file.png;rm', 'nested/file.png', 'link', '', 'x' * 200 + '.png']:
            with self.assertRaises(c.MaintenanceError): c.cleanup(self.deployment, name)
        with self.assertRaises(c.MaintenanceError): c.cleanup(self.deployment, 'orphan.png', True)
        self.deployment.inspect.assert_not_called()
        self.command.assert_not_called()

    def test_running_application_aborts_before_inventory(self):
        self.deployment.require_stopped.side_effect = c.MaintenanceError('running')
        with self.assertRaises(c.MaintenanceError): c.cleanup(self.deployment)
        self.deployment.archive.assert_not_called()
        self.command.assert_not_called()

    def test_active_legacy_and_missing_references_are_preserved(self):
        for name in ['active.png', 'orphan.png']:
            self.deployment.sql.return_value = '/uploads/orphan.png'
            with self.assertRaises(c.MaintenanceError): c.cleanup(self.deployment, name, True, 'bundle')
        self.deployment.sql.return_value = '/uploads/missing.png'
        with self.assertRaises(c.MaintenanceError): c.cleanup(self.deployment)
        self.command.assert_not_called()

    def test_different_source_or_backup_bytes_prevent_deletion(self):
        self.manifest['source_project'] = 'other-project'
        with self.assertRaises(c.MaintenanceError): c.cleanup(self.deployment, 'orphan.png', True, 'bundle')
        self.manifest['source_project'] = 'fixture-project'
        self.manifest['uploads'] = {}
        with self.assertRaises(c.MaintenanceError): c.cleanup(self.deployment, 'orphan.png', True, 'bundle')
        self.command.assert_not_called()

    def test_apply_uses_exact_backed_up_file_and_rechecks_stopped_state(self):
        result = c.cleanup(self.deployment, 'orphan.png', True, 'bundle')
        self.assertTrue(result['applied'])
        self.assertEqual(self.command.call_args.args[0][-2:], ['/data/orphan.png', 'b' * 64])
        self.assertGreaterEqual(self.deployment.require_stopped.call_count, 3)

    def test_missing_file_is_idempotent_noop_and_changed_metadata_aborts(self):
        result = c.cleanup(self.deployment, 'already-gone.png', True, 'bundle')
        self.assertEqual(result['result'], 'absent')
        self.command.assert_not_called()
        self.deployment.metadata.side_effect = [{'photo_references': []}, {'photo_references': ['/uploads/orphan.png']}]
        with self.assertRaises(c.MaintenanceError): c.cleanup(self.deployment, 'orphan.png', True, 'bundle')
        self.command.assert_not_called()

    def test_filesystem_failure_propagates_and_does_not_claim_success(self):
        self.command.side_effect = c.MaintenanceError('permission failure')
        with self.assertRaises(c.MaintenanceError): c.cleanup(self.deployment, 'orphan.png', True, 'bundle')
        self.assertEqual(self.command.call_count, 1)
