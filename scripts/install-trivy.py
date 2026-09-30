#!/usr/bin/env python3
"""Install an official checksummed scanner locally; no system installation."""
import hashlib
import io
import platform
from pathlib import Path
import tarfile
import urllib.request

VERSION = '0.74.0'
ARCHIVES = {
    ('Linux', 'x86_64'): ('Linux-64bit', '2ae6fe3ee734b7fdf11335663e18c75ea12dccc76062f09f164a3b0f8be4371a'),
    ('Darwin', 'arm64'): ('macOS-ARM64', '1caada5e0e2091909357c7525d3aa76f4b660b13821bc143b190c7483e31cc11'),
}


def main():
    name, expected = ARCHIVES[(platform.system(), platform.machine())]
    url = f'https://github.com/aquasecurity/trivy/releases/download/v{VERSION}/trivy_{VERSION}_{name}.tar.gz'
    with urllib.request.urlopen(url, timeout=120) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != expected:
        raise RuntimeError('Scanner archive checksum mismatch')
    output = Path(__file__).resolve().parents[1] / 'target' / 'tools' / 'trivy'
    output.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as archive:
        member = archive.getmember('trivy')
        if not member.isfile():
            raise RuntimeError('Scanner is not a regular file')
        with archive.extractfile(member) as stream:
            output.write_bytes(stream.read())
    output.chmod(0o755)
    print(output)


if __name__ == '__main__':
    main()
