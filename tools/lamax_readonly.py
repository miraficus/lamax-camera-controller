#!/usr/bin/env python3
"""LAMAX Hi Cam protocol probe. Camera operations are GET/read-only only.

Python 3.9+, standard library. No camera-control, delete or upgrade commands.
Protocol derived from user-supplied APK, not copied from the reference client.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys
from urllib.parse import quote, urlsplit
from urllib.request import Request, build_opener, ProxyHandler, HTTPRedirectHandler


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Unexpected redirect; stopping instead of following it")


def parse_vars(text):
    # Never evaluate camera responses as JavaScript.
    return dict(re.findall(r'\bvar\s+(\w+)\s*=\s*"([^"\r\n]*)"\s*;', text))


def media_path(path):
    # Explicit download of camera media only; preserve letter case.
    if not isinstance(path, str) or any(c in path for c in ('\\', '%', '?', '#', '\x00')):
        raise ValueError("Invalid media path")
    if any(ord(c) < 32 for c in path):
        raise ValueError("Control character in path")
    p = PurePosixPath(path)
    if path.startswith('/') or '..' in p.parts or p.parts[:2] != ('sd', 'DCIM'):
        raise ValueError("Path must be relative and inside sd/DCIM/")
    if p.suffix.upper() not in ('.JPG', '.JPEG', '.MP4', '.LRV', '.THM'):
        raise ValueError("Unsupported media extension")
    return p.as_posix()


class Camera:
    def __init__(self, base_url, timeout=10):
        u = urlsplit(base_url)
        if u.scheme != 'http' or not u.hostname or u.username or u.password or u.path not in ('', '/') or u.query or u.fragment:
            raise ValueError("Use an HTTP origin, e.g. http://192.168.0.1")
        self.base = base_url.rstrip('/')
        self.timeout = timeout
        # Direct camera connection, independent of host proxy environment variables.
        self.opener = build_opener(ProxyHandler({}), NoRedirect())

    def open(self, path):
        req = Request(self.base + '/' + path, headers={'User-Agent': 'LamaxReadOnly/0.1', 'Accept-Encoding': 'identity'})
        return self.opener.open(req, timeout=self.timeout)

    def text(self, path):
        with self.open(path) as response:
            data = response.read(4 * 1024 * 1024 + 1)
        if len(data) > 4 * 1024 * 1024:
            raise ValueError("Response exceeds 4 MiB limit")
        return data.decode('utf-8-sig')

    def cgi(self, endpoint):
        return self.text('cgi-bin/hi3510/' + endpoint)

    def listing(self, page_size=100, max_pages=100):
        raw = self.cgi('getfilecount.cgi?')
        count = int(parse_vars(raw)['count'])
        if count < 0 or count > page_size * max_pages:
            raise ValueError("Unexpected file count; inspect the raw response")
        result, seen = [], set()
        for start in range(1, count + 1, page_size):
            end = min(start + page_size - 1, count)
            page = json.loads(self.cgi(f'getfilelistinfoios.cgi?&-start={start}&-end={end}'))
            if not isinstance(page, list):
                raise ValueError("Expected a JSON array of media files")
            for item in page:
                if not isinstance(item, dict) or not isinstance(item.get('path'), str):
                    raise ValueError("Invalid file-list entry")
                if item['path'] not in seen:
                    seen.add(item['path'])
                    result.append(item)
        return {'reported_count': count, 'unique_entries': len(result), 'files': result}

    def download(self, remote, destination, expected_size=None):
        remote = media_path(remote)
        target = Path(destination)
        if target.exists():
            raise FileExistsError(f"Refusing to overwrite {target}")
        part = target.with_name(target.name + '.part')
        size, digest = 0, hashlib.sha256()
        # Exclusive creation also prevents accidentally following a pre-existing symlink.
        with part.open('xb') as output:
            with self.open(quote(remote, safe='/')) as response:
                length = response.headers.get('Content-Length')
                while True:
                    chunk = response.read(128 * 1024)
                    if not chunk:
                        break
                    output.write(chunk)
                    digest.update(chunk)
                    size += len(chunk)
            output.flush()
            os.fsync(output.fileno())
        if length is not None and size != int(length):
            raise ValueError("Incomplete download; .part file retained")
        if expected_size is not None and size != expected_size:
            raise ValueError("Size differs from file list; .part file retained")
        # Link creation fails if destination appeared meanwhile; never overwrite it.
        os.link(part, target)
        part.unlink()
        return {'path': remote, 'local_file': str(target), 'bytes': size, 'sha256': digest.hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://192.168.0.1')
    parser.add_argument('--timeout', type=float, default=10)
    sub = parser.add_subparsers(dest='command', required=True)
    sub.add_parser('probe', help='Read device, battery, SD status and file count')
    listing = sub.add_parser('list', help='Read the complete file list as JSON')
    listing.add_argument('--page-size', type=int, default=100, choices=range(1, 201), metavar='1..200')
    down = sub.add_parser('download', help='Download one explicitly selected media file')
    down.add_argument('remote_path')
    down.add_argument('local_path')
    down.add_argument('--expected-size', type=int)
    args = parser.parse_args()
    try:
        camera = Camera(args.base_url, args.timeout)
        if args.command == 'probe':
            output = {}
            for name in ('getdeviceattr.cgi', 'getbatterycapacity.cgi?', 'getsdstate.cgi?', 'getfilecount.cgi?'):
                try:
                    raw = camera.cgi(name)
                    output[name] = {'raw': raw, 'values': parse_vars(raw)}
                except Exception as exc:
                    output[name] = {'error': str(exc)}
            print(json.dumps(output, ensure_ascii=False, indent=2))
            return int(any('error' in entry for entry in output.values()))
        elif args.command == 'list':
            output = camera.listing(args.page_size)
        else:
            output = camera.download(args.remote_path, args.local_path, args.expected_size)
        print(json.dumps(output, ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:
        print(f'Error: {exc}', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
