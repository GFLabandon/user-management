#!/usr/bin/env python3
"""Rehearse 4C using ONLY fresh random projects; deletes only those test resources."""
import hashlib
import http.cookiejar
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import secrets
import signal
import shutil
import socket
import struct
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zlib
import maintenance as m


def interrupted(signum, frame):
    raise SystemExit(128 + signum)


signal.signal(signal.SIGTERM, interrupted)


class Inputs(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.values = {}
        self.feed(text)

    def handle_starttag(self, tag, attributes):
        attributes = dict(attributes)
        if tag == 'input' and 'name' in attributes:
            self.values[attributes['name']] = attributes.get('value', '')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None


class Browser:
    def __init__(self, port):
        self.base = f'http://127.0.0.1:{port}'
        self.opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(self, path, data=None, content_type=None):
        if isinstance(data, dict):
            data = urllib.parse.urlencode(data).encode()
        request = urllib.request.Request(self.base + path, data=data)
        if content_type:
            request.add_header('Content-Type', content_type)
        try:
            response = self.opener.open(request, timeout=12)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            location = response.headers.get('Location')
            if location:
                absolute = urllib.parse.urlsplit(urllib.parse.urljoin(self.base + path, location))
                assert absolute.netloc == urllib.parse.urlsplit(self.base).netloc
                location = absolute.path + ('?' + absolute.query if absolute.query else '')
            return response.status, response.read(), location

    def token(self, path):
        code, body, _ = self.request(path)
        assert code == 200, (path, code)
        return Inputs(body.decode()).values['_csrf']

    def login(self, username, password):
        code, _, location = self.request('/login', {'_csrf': self.token('/login'), 'username': username, 'password': password})
        assert (code, location) == (302, '/counselors')


def png():
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0)) + \
        chunk(b'IDAT', zlib.compress(b'\x00\x20\x80\xc0')) + chunk(b'IEND', b'')


def multipart(fields, image=True):
    boundary = secrets.token_hex(16)
    body = b''
    for name, value in fields.items():
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
    if image:
        body += f'--{boundary}\r\nContent-Disposition: form-data; name="photo"; filename="recovery.png"\r\nContent-Type: image/png\r\n\r\n'.encode() + png() + b'\r\n'
    body += f'--{boundary}--\r\n'.encode()
    return body, 'multipart/form-data; boundary=' + boundary


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def cli(*args, success=True):
    result = subprocess.run([str(m.ROOT / 'scripts' / args[0])] + list(args[1:]),
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=m.clean_env(), timeout=600)
    assert (result.returncode == 0) == success, result.stderr.decode()
    return result.stdout.decode()


def main():
    os.umask(0o077)
    name = 'counselor-recovery-' + secrets.token_hex(5)
    report = m.ROOT / 'target' / 'recovery-verification' / name
    report.mkdir(parents=True)
    checks = []
    def passed(message):
        checks.append(message)
        print('PASS ' + message, flush=True)
    with tempfile.TemporaryDirectory(prefix=name + '-') as temporary:
        work = Path(temporary)
        admin_password, viewer_password = secrets.token_urlsafe(24), secrets.token_urlsafe(24)
        deployments = []
        for suffix in ('source', 'target', 'bad'):
            env_file = work / (suffix + '.env')
            app_port = port()
            env_file.write_text(f'DB_PASSWORD={secrets.token_urlsafe(24)}\nMYSQL_ROOT_PASSWORD={secrets.token_urlsafe(24)}\n'
                                f'APP_BOOTSTRAP_USERNAME=recovery_admin\nAPP_BOOTSTRAP_PASSWORD={admin_password}\nAPP_PORT={app_port}\n')
            env_file.chmod(0o600)
            deployment = m.Deployment(name + '-' + suffix, env_file)
            deployment.require_new_project()
            deployments.append((deployment, app_port))
        (source, source_port), (target, target_port), (bad, _) = deployments
        bundle = work / 'bundle'
        try:
            source.compose('up', '-d', '--no-build', '--wait', '--wait-timeout', '180')
            browser = Browser(source_port)
            browser.login('recovery_admin', admin_password)
            assert browser.request('/departments', {'_csrf': browser.token('/departments/new'), 'name': '恢复演练院系', 'active': 'true', 'version': '0'})[0] == 302
            department = source.sql("SELECT id FROM departments WHERE name='恢复演练院系'")
            assert department.isdigit()
            fields = {'_csrf': browser.token('/counselors/new'), 'employeeNo': 'RECOVERY-001', 'name': '恢复演练档案',
                      'departmentId': department, 'employmentStatus': 'ACTIVE', 'version': '0', 'remark': '备份前备注'}
            body, kind = multipart(fields)
            code, _, record = browser.request('/counselors', body, kind)
            assert code == 302 and re.fullmatch(r'/counselors/\d+', record)
            image_path = source.sql("SELECT photo_path FROM counselors WHERE employee_no='RECOVERY-001'")
            image_hash = hashlib.sha256(browser.request(image_path)[1]).hexdigest()
            assert browser.request(record + '/deactivate', {'_csrf': browser.token(record), 'version': '0'})[0] == 302
            assert browser.request('/accounts', {'_csrf': browser.token('/accounts/new'), 'username': 'recovery_viewer',
                    'password': viewer_password, 'role': 'VIEWER', 'enabled': 'true', 'version': '0'})[0] == 302
            source_volumes = source.volumes()
            source_image = source.inspect('app')['Image']
            m.run(m.volume_command(source_volumes['uploads'], source_image, readonly=False) +
                  ['sh', '-c', 'cat > /data/orphan.png'], data=png())
            passed('source fixture: two accounts, Chinese counselor, status history, audit and private image plus orphan')
            cli('cleanup-images.py', '--project', source.project, '--env-file', str(source.env_file), success=False)
            cli('backup.sh', '--project', source.project, '--env-file', str(source.env_file), '--output', str(bundle))
            source.require_stopped()
            manifest = m.validate_bundle(bundle)
            assert manifest['counts']['system_accounts'] == 2
            assert manifest['counts']['counselor_status_history'] == 2
            assert manifest['counts']['audit_events'] > 0
            assert 'orphan.png' in manifest['uploads']
            assert all(p.stat().st_mode & 0o077 == 0 for p in [bundle, *bundle.iterdir()])
            passed('coordinated backup leaves application stopped and contains complete private checksummed bundle')
            cleanup_args = ['--project', source.project, '--env-file', str(source.env_file)]
            preview = json.loads(cli('cleanup-images.py', *cleanup_args))
            assert preview['candidates'] == ['orphan.png'] and not preview['applied']
            cli('cleanup-images.py', *cleanup_args, '--file', image_path[9:], '--apply', '--backup', str(bundle), success=False)
            cli('cleanup-images.py', *cleanup_args, '--file', '../orphan.png', success=False)
            cli('cleanup-images.py', *cleanup_args, '--file', 'orphan.png', '--apply', success=False)
            source.sql(f"INSERT INTO users(username,note,dept_id,photo_path) VALUES ('legacy-image-guard','synthetic',{department},'/uploads/orphan.png')")
            cli('cleanup-images.py', *cleanup_args, '--file', 'orphan.png', '--apply', '--backup', str(bundle), success=False)
            source.sql("DELETE FROM users WHERE username='legacy-image-guard'")
            m.run(m.volume_command(source_volumes['uploads'], source_image, readonly=False) + ['sh', '-c', 'cat > /data/orphan.png'], data=b'changed')
            cli('cleanup-images.py', *cleanup_args, '--file', 'orphan.png', '--apply', '--backup', str(bundle), success=False)
            m.run(m.volume_command(source_volumes['uploads'], source_image, readonly=False) + ['sh', '-c', 'cat > /data/orphan.png'], data=png())
            passed('offline image preview rejects running app, active and legacy references, traversal, missing backup and changed bytes')
            old_mode = m.run(m.volume_command(source_volumes['uploads'], source_image, readonly=False) + ['stat', '-c', '%a', '/data']).decode().strip()
            try:
                m.run(m.volume_command(source_volumes['uploads'], source_image, readonly=False) + ['chmod', '500', '/data'])
                cli('cleanup-images.py', *cleanup_args, '--file', 'orphan.png', '--apply', '--backup', str(bundle), success=False)
            finally:
                m.run(m.volume_command(source_volumes['uploads'], source_image, readonly=False) + ['chmod', old_mode, '/data'])
            deleted = json.loads(cli('cleanup-images.py', *cleanup_args, '--file', 'orphan.png', '--apply', '--backup', str(bundle)))
            assert deleted['result'] == 'deleted' and deleted['applied']
            repeated = json.loads(cli('cleanup-images.py', *cleanup_args, '--file', 'orphan.png', '--apply', '--backup', str(bundle)))
            assert repeated['result'] == 'absent' and not repeated['applied']
            assert m.validate_bundle(bundle)['uploads']['orphan.png'] == manifest['uploads']['orphan.png']
            source.require_stopped()
            passed('failed offline deletion is retryable and idempotent; original image remains recoverable in unchanged backup')
            # Verify the real server lock blocks writes, then releases on an exception.
            try:
                with m.database_read_lock(source):
                    try:
                        source.sql("SET SESSION lock_wait_timeout=1; INSERT INTO departments(name) VALUES ('lock-blocked')")
                    except m.MaintenanceError:
                        pass
                    else:
                        raise AssertionError('Database writes were not locked')
                    raise RuntimeError('simulated backup failure')
            except RuntimeError:
                pass
            source.sql("INSERT INTO departments(name) VALUES ('lock-released')")
            source.sql("DELETE FROM departments WHERE name='lock-released'")
            passed('real database lock blocks writes and releases when backup work raises an error')
            broken = work / 'broken'
            shutil.copytree(bundle, broken)
            with (broken / 'database.sql').open('ab') as stream:
                stream.write(b'corruption')
            cli('restore.sh', '--project', bad.project, '--env-file', str(bad.env_file), '--backup', str(broken), success=False)
            bad.require_new_project()
            (broken / 'uploads.tar').unlink()
            cli('restore.sh', '--project', bad.project, '--env-file', str(bad.env_file), '--backup', str(broken), success=False)
            bad.require_new_project()
            passed('corrupt or missing backup refuses restore before creating target resources')
            started = time.monotonic()
            output = cli('restore.sh', '--project', target.project, '--env-file', str(target.env_file), '--backup', str(bundle))
            target.require_stopped()
            assert target.metadata() == {key: manifest[key] for key in ('counts', 'flyway', 'photo_references')}
            restored_hash = target.sql("SELECT password_hash FROM system_accounts WHERE username='recovery_admin'")
            assert restored_hash == source.sql("SELECT password_hash FROM system_accounts WHERE username='recovery_admin'")
            passed('fresh target restores all table rows, Flyway history, password hashes and exact upload inventory')
            # Start existing restored containers, do not recreate them using a mutable app image tag.
            container_before = target.inspect('app')
            target.compose('start', 'app')
            target.wait_healthy('app', timeout=120)
            container_after = target.inspect('app')
            assert (container_after['Id'], container_after['Image']) == (container_before['Id'], manifest['app_image'])
            restored = Browser(target_port)
            restored.login('recovery_admin', admin_password)
            assert '恢复演练档案' in restored.request('/counselors?keyword=RECOVERY-001')[1].decode()
            assert hashlib.sha256(restored.request(image_path)[1]).hexdigest() == image_hash
            viewer = Browser(target_port)
            viewer.login('recovery_viewer', viewer_password)
            assert viewer.request(record)[0] == 200 and viewer.request('/accounts')[0] == 403
            assert Browser(target_port).request(image_path)[0] == 302
            fields.update({'_csrf': restored.token(record + '/edit'), 'version': '1', 'employmentStatus': 'ACTIVE', 'remark': '恢复后编辑成功'})
            body, kind = multipart(fields, image=False)
            assert restored.request(record, body, kind)[0] == 302
            assert target.sql("SELECT version FROM counselors WHERE employee_no='RECOVERY-001'") == '2'
            assert target.sql('SELECT COUNT(*) FROM counselor_status_history') == '3'
            assert '恢复后编辑成功' in restored.request(record)[1].decode()
            elapsed = round(time.monotonic() - started, 1)
            passed('restored HTTP login/search/edit/image and viewer permissions work; status history continues')
            before = target.metadata()
            cli('restore.sh', '--project', target.project, '--env-file', str(target.env_file), '--backup', str(bundle), success=False)
            assert target.metadata() == before and target.inspect('app')['State']['Running']
            cli('restore.sh', '--project', source.project, '--env-file', str(source.env_file), '--backup', str(bundle), success=False)
            passed('existing nonempty target and original source are refused without stopping or changing target')
            # Force a failed backup in the disposable source; it must never create a valid manifest.
            filename = image_path.removeprefix('/uploads/')
            assert m.FILE_NAME.fullmatch(filename)
            m.run(m.volume_command(source_volumes['uploads'], source_image, readonly=False) + ['rm', '/data/' + filename])
            incomplete = work / 'incomplete'
            cli('backup.sh', '--project', source.project, '--env-file', str(source.env_file), '--output', str(incomplete), success=False)
            assert (incomplete / '.incomplete').exists() and not (incomplete / 'manifest.json').exists()
            source.require_stopped()
            source.sql("SET SESSION lock_wait_timeout=2; INSERT INTO departments(name) VALUES ('after-failure')")
            passed('missing referenced photo leaves backup incomplete, source stopped and database lock released')
            (report / 'result.json').write_text(json.dumps({'passed': True, 'checks': checks,
                'restore_through_http_seconds': elapsed, 'restore_command_result': output.strip(),
                'source_commit': manifest['source_checkout_commit'], 'app_image': manifest['app_image'],
                'mysql': manifest['mysql_version'], 'counts_at_backup': manifest['counts']}, ensure_ascii=False, indent=2))
            print(f'Evidence: {report}', flush=True)
        except BaseException:
            (report / 'result.json').write_text(json.dumps({'passed': False, 'checks': checks,
                'error': 'Recovery suite interrupted or failed; inspect local diagnostics.'}, indent=2))
            raise
        finally:
            # Backup contents include credential hashes: delete test artifacts, retain only summary evidence.
            for deployment, _ in deployments:
                deployment.compose('down', '--volumes', '--remove-orphans')


if __name__ == '__main__':
    main()
