#!/usr/bin/env python3
"""Inspect unreferenced images, or retry one deletion in an explicit offline maintenance window."""
import argparse
import json
import os
from pathlib import Path
import re
import sys
import tempfile
import tarfile
from datetime import datetime, timezone
from maintenance import (Deployment, MaintenanceError, require, run, project_lock,
                         database_read_lock, archive_inventory, validate_bundle, volume_command)

NAME = re.compile(r'[A-Za-z0-9_-]+\.(?:png|jpg|jpeg)')


def checked_name(value):
    require(isinstance(value, str) and len(value) <= 191 and NAME.fullmatch(value), 'Invalid image filename.')
    return value


def backup_allows(manifest, project, name, inventory):
    require(manifest['source_project'] == project, 'Backup belongs to another project.')
    require(manifest['uploads'].get(name) == inventory[name], 'Backup does not contain the current image bytes.')


def cleanup(deployment, name=None, apply=False, backup=None):
    if name is not None:
        checked_name(name)
    require(not apply or (name and backup), '--apply requires one --file and a verified --backup.')
    # Check the backup before touching a deployment. Its archive remains available for manual recovery.
    manifest = validate_bundle(Path(backup)) if apply else None
    app, db = deployment.inspect('app'), deployment.inspect('db')
    deployment.require_standard_connections(app, db)
    volumes = deployment.volumes()
    deployment.require_stopped()
    require(db['State']['Running'] and db['State'].get('Health', {}).get('Status') == 'healthy', 'Database must be healthy.')
    require(not db['HostConfig'].get('PortBindings'), 'Cleanup requires the private Compose database.')
    with database_read_lock(deployment):
        metadata = deployment.metadata()
        with tempfile.TemporaryDirectory(prefix='counselor-cleanup-') as temporary:
            archive = Path(temporary) / 'uploads.tar'
            deployment.archive(archive, volumes['uploads'], app['Image'])
            inventory = archive_inventory(archive)
        references = set(metadata['photo_references'])
        references.update(deployment.sql("SELECT DISTINCT photo_path FROM users WHERE photo_path IS NOT NULL AND photo_path <> ''").splitlines())
        for ref in references:
            require(ref.startswith('/uploads/') and ref[9:] in inventory, 'Referenced photo is missing or invalid; retain all files for review.')
        candidates = sorted(file for file in inventory if '/uploads/' + file not in references)
        result = {'project': deployment.project, 'at': datetime.now(timezone.utc).isoformat(), 'applied': False}
        if not name:
            result.update(result='preview', candidates=candidates)
        else:
            require('/uploads/' + name not in references, 'Image is still referenced; nothing was removed.')
            if name not in inventory:
                result.update(result='absent', file=name)
            elif not apply:
                result.update(result='candidate', file=name, **inventory[name])
            else:
                backup_allows(manifest, deployment.project, name, inventory)
                deployment.require_stopped()
                require(metadata == deployment.metadata(), 'Database changed; nothing was removed.')
                # Managed volume, fixed filename, no links, same bytes as the recovery copy.
                # Direct Docker/file operators must stay out of this offline window.
                script = ('set -eu; test ! -L "$1"; test -f "$1"; '
                          'actual=$(sha256sum "$1"); test "${actual%% *}" = "$2"; rm -- "$1"')
                run(volume_command(volumes['uploads'], app['Image'], readonly=False)
                    + ['sh', '-c', script, 'cleanup-image', '/data/' + name, inventory[name]['sha256']])
                deployment.require_stopped()
                result.update(result='deleted', applied=True, file=name, sha256=inventory[name]['sha256'])
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', required=True)
    parser.add_argument('--env-file', required=True)
    parser.add_argument('--file', help='One plain stored filename, never a path. Omit to list candidates.')
    parser.add_argument('--apply', action='store_true', help='Remove only the named, unreferenced, backed-up image.')
    parser.add_argument('--backup', help='Existing verified backup bundle from the same project.')
    args = parser.parse_args()
    os.umask(0o077)
    try:
        deployment = Deployment(args.project, args.env_file)
        with project_lock(deployment.project):
            print(json.dumps(cleanup(deployment, args.file, args.apply, args.backup), ensure_ascii=False))
        return 0
    except (MaintenanceError, OSError, ValueError, KeyError, TypeError, tarfile.TarError) as error:
        message = str(error) if isinstance(error, MaintenanceError) else 'Invalid filesystem, backup or configuration data.'
        print('Image cleanup failed: ' + message + ' Application was not restarted; keep the backup for recovery.', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
