#!/usr/bin/env python3
"""One local/CI entry point; run all suites and preserve a summary even on failure."""
import json
import signal
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SUITES = ('verify-compose.py', 'verify-migrations.py', 'verify-recovery.py')


def main():
    results = []
    child = None
    def interrupted(signum, frame):
        raise SystemExit(128 + signum)
    signal.signal(signal.SIGTERM, interrupted)
    try:
        for suite in SUITES:
            started = time.monotonic()
            child = subprocess.Popen([sys.executable, '-B', str(ROOT / 'scripts' / suite)], cwd=ROOT)
            try:
                code = child.wait(timeout=900)
            except subprocess.TimeoutExpired:
                child.terminate()
                code = 124
                child.wait(timeout=60)
            results.append({'suite': suite, 'exit_code': code, 'seconds': round(time.monotonic() - started, 1)})
    finally:
        if child is not None and child.poll() is None:
            child.terminate()
            try:
                child.wait(timeout=60)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
        (ROOT / 'target').mkdir(exist_ok=True)
        (ROOT / 'target' / 'mysql-ci-summary.json').write_text(json.dumps({'passed':
            len(results) == len(SUITES) and all(r['exit_code'] == 0 for r in results), 'suites': results}, indent=2))
    return int(any(r['exit_code'] for r in results))


if __name__ == '__main__':
    raise SystemExit(main())
