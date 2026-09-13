#!/usr/bin/env python3
"""Destructive only to a fresh, randomly named Compose project created by this run.
Requires a previously built campus-counselor-management:local image. Uses Python stdlib.
"""
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import socket
import struct
import zlib
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser

ROOT = Path(__file__).resolve().parents[1]
PROJECT = "counselor-verify-" + secrets.token_hex(6)
REPORT = ROOT / "target" / "compose-verification" / PROJECT
REPORT.mkdir(parents=True)
os.chmod(REPORT, 0o700)
ENV = {k: v for k, v in os.environ.items() if not k.startswith(("COMPOSE_", "APP_", "DB_", "MYSQL_"))}
TEMP = tempfile.TemporaryDirectory(prefix=PROJECT + "-")
ENVFILE = Path(TEMP.name) / ".env.compose"
OVERRIDE = Path(TEMP.name) / "acceptance.yaml"
with socket.socket() as available:
    available.bind(("127.0.0.1", 0))
    PORT = available.getsockname()[1]
BASE = f"http://127.0.0.1:{PORT}"
DB_PASSWORD, ROOT_PASSWORD, ADMIN_PASSWORD, VIEWER_PASSWORD = [secrets.token_urlsafe(24) for _ in range(4)]
SECRETS = [DB_PASSWORD, ROOT_PASSWORD, ADMIN_PASSWORD, VIEWER_PASSWORD]
COMPOSE = ["docker", "compose", "--project-name", PROJECT, "--env-file", str(ENVFILE),
           "-f", str(ROOT / "compose.yaml"), "-f", str(OVERRIDE)]
CHECKS = []


def redact(text):
    for secret in SECRETS:
        text = text.replace(secret, "[REDACTED]")
    return text


def command(args, *, input=None, check=True, timeout=240):
    result = subprocess.run(args, cwd=ROOT, env=ENV, input=input, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(redact(f"Command failed ({args[0]}): {result.stdout}\n{result.stderr}"))
    return result


def compose(*args, **kwargs):
    return command(COMPOSE + list(args), **kwargs)


def configure(bootstrap):
    ENVFILE.write_text(f"DB_PASSWORD='{DB_PASSWORD}'\nMYSQL_ROOT_PASSWORD='{ROOT_PASSWORD}'\n"
                       f"APP_BOOTSTRAP_USERNAME='{ 'acceptance_admin' if bootstrap else '' }'\n"
                       f"APP_BOOTSTRAP_PASSWORD='{ADMIN_PASSWORD if bootstrap else ''}'\nAPP_PORT={PORT}\n")
    ENVFILE.chmod(0o600)


def state(service):
    cid = compose("ps", "--all", "--quiet", service).stdout.strip()
    if not cid:
        return {}
    return json.loads(command(["docker", "inspect", "--format", "{{json .State}}", cid]).stdout)


def until(predicate, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if predicate():
                return
        except (OSError, urllib.error.URLError):
            pass
        time.sleep(1)
    raise AssertionError("Timed out waiting for acceptance condition")


def passed(label):
    CHECKS.append(label)
    print("PASS " + label, flush=True)


def sql(query):
    # Password stays in the container environment, never in command-line arguments.
    return compose("exec", "-T", "db", "sh", "-c",
                   'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -B',
                   input=query + ";\n").stdout.strip()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Inputs(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.values = {}
        self.feed(html)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "input" and "name" in attrs:
            self.values[attrs["name"]] = attrs.get("value", "")


class Browser:
    def __init__(self):
        self.cookies = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies), NoRedirect())

    def request(self, path, data=None, content_type=None, headers=None):
        if isinstance(data, dict):
            data = urllib.parse.urlencode(data).encode()
        req = urllib.request.Request(BASE + path, data=data, headers=headers or {})
        if content_type:
            req.add_header("Content-Type", content_type)
        try:
            result = self.opener.open(req, timeout=12)
        except urllib.error.HTTPError as failure:
            result = failure
        with result:
            if "Location" in result.headers:
                redirect = urllib.parse.urlsplit(urllib.parse.urljoin(BASE + path, result.headers["Location"]))
                assert (redirect.scheme, redirect.netloc) == ("http", f"127.0.0.1:{PORT}"), "Unexpected redirect origin"
                result.headers.replace_header("Location", redirect.path + ("?" + redirect.query if redirect.query else ""))
            return result.code, result.read(), result.headers

    def csrf(self, path):
        code, body, _ = self.request(path)
        assert code == 200, (path, code)
        return Inputs(body.decode()).values["_csrf"]

    def login(self, username, password):
        code, _, headers = self.request("/login", {"_csrf": self.csrf("/login"), "username": username, "password": password})
        assert code == 302 and headers["Location"] == "/counselors", (code, headers.get("Location"))


def multipart(fields):
    boundary = "CounselorBoundary" + secrets.token_hex(12)
    body = bytearray()
    for key, value in fields.items():
        body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
    # Generate a valid 1x1 RGB PNG with checked chunk CRCs.
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(b"\x00\x20\x80\xc0")) + chunk(b"IEND", b"")
    body.extend(f'--{boundary}\r\nContent-Disposition: form-data; name="photo"; filename="acceptance.png"\r\nContent-Type: image/png\r\n\r\n'.encode())
    body.extend(png)
    body.extend(f'\r\n--{boundary}--\r\n'.encode())
    return bytes(body), "multipart/form-data; boundary=" + boundary


def health(browser, group, expected):
    code, body, headers = browser.request("/actuator/health/" + group)
    assert code == expected, (group, code)
    assert set(json.loads(body)) == {"status"}
    assert "Set-Cookie" not in headers
    return True


def snapshot():
    return sql("SELECT CONCAT((SELECT COUNT(*) FROM system_accounts), ':', (SELECT COUNT(*) FROM counselors), ':', "
               "(SELECT COUNT(*) FROM departments), ':', (SELECT COUNT(*) FROM counselor_status_history))"), \
        sql("SELECT password_hash FROM system_accounts WHERE username='acceptance_admin'")


try:
    command(["docker", "image", "inspect", "campus-counselor-management:local"])
    configure(False)
    # Simulate slow database availability; suppress restart loops only in this test project.
    OVERRIDE.write_text('services:\n  db:\n    entrypoint: ["sh", "-c", "sleep 12; exec docker-entrypoint.sh mysqld"]\n'
                        '  app:\n    restart: "no"\n')
    with (REPORT / "startup.log").open("w") as output:
        starting = subprocess.Popen(COMPOSE + ["up", "-d", "--no-build"], cwd=ROOT, env=ENV,
                                    stdout=output, stderr=subprocess.STDOUT)
        try:
            until(lambda: state("db").get("Running"), timeout=30)
            assert not state("app").get("Running", False)
            assert state("db").get("Health", {}).get("Status") == "starting"
            passed("delayed database keeps application from starting early")
            assert starting.wait(timeout=180) == 0
        finally:
            if starting.poll() is None:
                starting.terminate()
                starting.wait(timeout=15)
    until(lambda: state("app").get("Status") == "exited")
    assert state("app")["ExitCode"] != 0
    assert sql("SELECT COUNT(*) FROM system_accounts") == "0"
    assert sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL") == "4"
    passed("empty database migrates V1-V4 but refuses startup without bootstrap credentials")
    configure(True)
    compose("up", "-d", "--no-build", "--wait", "--wait-timeout", "180", "app")
    anon, admin, viewer = Browser(), Browser(), Browser()
    health(anon, "liveness", 200)
    health(anon, "readiness", 200)
    admin.login("acceptance_admin", ADMIN_PASSWORD)
    assert anon.request("/accounts")[0] == 302
    for path in ["/actuator", "/actuator/health", "/actuator/env", "/actuator/beans", "/actuator/health/readiness/db"]:
        assert anon.request(path)[0] == 403
        assert admin.request(path)[0] == 403
    assert sql("SELECT COUNT(*) FROM counselors") == "0"
    assert sql("SELECT COUNT(*) FROM system_accounts") == "1"
    passed("fresh deploy has no demo records, administrator login works and management endpoints stay closed")
    code, _, headers = admin.request("/departments", {"_csrf": admin.csrf("/departments/new"), "name": "容器验收院系", "active": "true", "version": "0"})
    assert code == 302
    department = sql("SELECT id FROM departments WHERE name='容器验收院系'")
    assert department.isdigit(), "Chinese department lookup must return its ID"
    fields = {"employeeNo": "CONTAINER-001", "name": "容器验收档案", "departmentId": department, "employmentStatus": "ACTIVE", "version": "0"}
    body, content_type = multipart(fields)
    assert admin.request("/counselors", body, content_type)[0] == 403
    fields["_csrf"] = admin.csrf("/counselors/new")
    body, content_type = multipart(fields)
    code, response_body, headers = admin.request("/counselors", body, content_type)
    if code != 302:
        page = response_body.decode()
        errors = re.findall(r'<[^>]*class="[^\"]*(?:error|invalid)[^\"]*"[^>]*>(.*?)</', page, re.S)
        print("Upload validation: " + redact(str(errors)), flush=True)
    assert code == 302 and re.fullmatch(r"/counselors/\d+", headers["Location"]), (code, headers.get("Location"))
    record = headers["Location"]
    photo = sql("SELECT photo_path FROM counselors WHERE employee_no='CONTAINER-001'")
    code, image, _ = admin.request(photo)
    assert code == 200 and image.startswith(b"\x89PNG")
    digest = hashlib.sha256(image).hexdigest()
    assert anon.request(photo)[0] == 302
    assert "容器验收档案" in admin.request("/counselors?keyword=CONTAINER-001")[1].decode()
    passed("real multipart CSRF, counselor create/search and private PNG upload/read")
    assert admin.request("/accounts", {"_csrf": admin.csrf("/accounts/new"), "username": "acceptance_viewer",
                                      "password": VIEWER_PASSWORD, "role": "VIEWER", "enabled": "true", "version": "0"})[0] == 302
    viewer.login("acceptance_viewer", VIEWER_PASSWORD)
    assert viewer.request(record)[0] == 200
    assert viewer.request("/accounts")[0] == 403
    assert viewer.request("/departments", {"_csrf": viewer.csrf("/counselors"), "name": "forged"})[0] == 403
    account_id = sql("SELECT id FROM system_accounts WHERE username='acceptance_viewer'")
    assert admin.request("/accounts/" + account_id, {"_csrf": admin.csrf("/accounts/" + account_id + "/edit"),
            "username": "acceptance_viewer", "password": "", "role": "VIEWER", "enabled": "false", "version": "0"})[0] == 302
    assert viewer.request("/counselors")[2]["Location"] == "/login?expired"
    passed("viewer cannot forge writes; disabling account revokes existing session")
    before = snapshot()
    app_id = compose("ps", "--quiet", "app").stdout.strip()
    details = json.loads(command(["docker", "inspect", app_id]).stdout)[0]
    assert details["Config"]["User"] == "10001:10001"
    assert details["HostConfig"]["ReadonlyRootfs"]
    assert details["HostConfig"]["PortBindings"]["8080/tcp"][0]["HostIp"] == "127.0.0.1"
    assert details["HostConfig"]["LogConfig"]["Config"] == {"max-file": "3", "max-size": "10m"}
    assert not json.loads(command(["docker", "inspect", compose("ps", "--quiet", "db").stdout.strip()]).stdout)[0]["HostConfig"]["PortBindings"]
    assert compose("exec", "-T", "app", "id", "-u").stdout.strip() == "10001"
    passed("non-root app, read-only root filesystem, bounded logs, loopback HTTP and no published database port")
    compose("stop", "db")
    health(anon, "liveness", 200)
    health(admin, "liveness", 200)
    health(anon, "readiness", 503)
    health(admin, "readiness", 503)
    code, _, headers = admin.request("/counselors?keyword=private-query-marker", headers={"X-Request-ID": "untrusted-id-marker"})
    assert code == 503
    failed_request_id = headers["X-Request-ID"]
    assert re.fullmatch(r"[0-9a-f-]{36}", failed_request_id)
    until(lambda: state("app").get("Health", {}).get("Status") == "unhealthy", timeout=90)
    assert compose("ps", "--quiet", "app").stdout.strip() == app_id
    passed("database outage returns readiness 503 and business 503 while liveness remains UP, without app restart")
    compose("start", "db")
    until(lambda: state("db").get("Health", {}).get("Status") == "healthy")
    until(lambda: state("app").get("Health", {}).get("Status") == "healthy")
    health(admin, "readiness", 200)
    assert admin.request(record)[0] == 200
    assert compose("ps", "--quiet", "app").stdout.strip() == app_id
    logs = compose("logs", "--no-color", "app").stdout
    assert failed_request_id in logs and "request_failed status=503" in logs
    for value in SECRETS + ["private-query-marker", "untrusted-id-marker"]:
        assert value not in logs
    (REPORT / "app-before-recreate.log").write_text(redact(logs))
    passed("database recovery restores readiness and existing login; request ID links sanitized error logs")
    configure(False)
    compose("up", "-d", "--no-build", "--no-deps", "--force-recreate", "--wait", "--wait-timeout", "120", "app")
    assert compose("ps", "--quiet", "app").stdout.strip() != app_id
    assert admin.request(record)[0] == 302  # Session is intentionally process-local.
    restored = Browser()
    restored.login("acceptance_admin", ADMIN_PASSWORD)
    assert restored.request(record)[0] == 200
    assert snapshot() == before
    assert hashlib.sha256(restored.request(photo)[1]).hexdigest() == digest
    passed("application recreation preserves account hash, records, history and image after bootstrap secrets are removed")
    # Recreate both containers, retaining this project's named volumes.
    compose("down")
    compose("up", "-d", "--no-build", "--wait", "--wait-timeout", "180")
    restored = Browser()
    restored.login("acceptance_admin", ADMIN_PASSWORD)
    assert snapshot() == before
    assert hashlib.sha256(restored.request(photo)[1]).hexdigest() == digest
    passed("full Compose down/up retains database and uploads volumes")
    (REPORT / "result.json").write_text(json.dumps({"project": PROJECT, "checks": CHECKS, "passed": True}, ensure_ascii=False, indent=2))
    print(f"Evidence: {REPORT}", flush=True)
except Exception as failure:
    (REPORT / "result.json").write_text(json.dumps({"project": PROJECT, "checks": CHECKS, "passed": False, "error": redact(str(failure))}, ensure_ascii=False, indent=2))
    raise
finally:
    # Never prune Docker globally. Only delete the random project this script created.
    try:
        logs = compose("logs", "--no-color", check=False).stdout
        (REPORT / "containers.log").write_text(redact(logs))
        compose("down", "--volumes", "--remove-orphans", timeout=180)
    finally:
        TEMP.cleanup()
