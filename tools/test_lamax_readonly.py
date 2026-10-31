import json
from pathlib import Path
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from lamax_readonly import Camera, media_path, parse_vars


class ProtocolTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.requests = []
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                cls.requests.append(self.path)
                bodies = {
                    '/cgi-bin/hi3510/getfilecount.cgi?': b'var count="2";\r\n',
                    '/cgi-bin/hi3510/getfilelistinfoios.cgi?&-start=1&-end=1': json.dumps([{'path': 'sd/DCIM/100GKUDV/SING0001.JPG', 'size': '5'}]).encode(),
                    '/cgi-bin/hi3510/getfilelistinfoios.cgi?&-start=2&-end=2': json.dumps([{'path': 'sd/DCIM/100GKUDV/NORM0001.MP4', 'size': '9'}]).encode(),
                    '/sd/DCIM/100GKUDV/SING0001.JPG': b'photo',
                }
                if self.path == '/redirect':
                    self.send_response(302)
                    self.send_header('Location', '/never-follow')
                    self.end_headers()
                    return
                if self.path not in bodies:
                    self.send_error(404)
                    return
                data = bodies[self.path]
                self.send_response(200)
                self.send_header('Content-Length', str(len(data)))
                self.end_headers()
                self.wfile.write(data)
            def log_message(self, *_):
                pass
        cls.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.camera = Camera(f'http://127.0.0.1:{cls.server.server_port}')

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def test_vars_without_execution(self):
        self.assertEqual(parse_vars('var count="2";\r\nvar x="1";'), {'count': '2', 'x': '1'})

    def test_paginated_listing(self):
        result = self.camera.listing(page_size=1)
        self.assertEqual(result['unique_entries'], 2)
        self.assertTrue(result['files'][0]['path'].endswith('.JPG'))

    def test_download_and_no_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / 'photo.jpg'
            self.camera.download('sd/DCIM/100GKUDV/SING0001.JPG', target, 5)
            self.assertEqual(target.read_bytes(), b'photo')
            with self.assertRaises(FileExistsError):
                self.camera.download('sd/DCIM/100GKUDV/SING0001.JPG', target)

    def test_size_mismatch_keeps_partial(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / 'photo.jpg'
            with self.assertRaises(ValueError):
                self.camera.download('sd/DCIM/100GKUDV/SING0001.JPG', target, 99)
            self.assertFalse(target.exists())
            self.assertTrue(target.with_name('photo.jpg.part').exists())

    def test_invalid_paths(self):
        for path in ['../main_app', '/main_app', 'http://example.org/x.JPG', 'sd/DCIM/../../main_app', 'sd/DCIM/%2e%2e/x.JPG', 'sd/DCIM/a.JPG?x=1']:
            with self.subTest(path=path), self.assertRaises(ValueError):
                media_path(path)

    def test_no_redirects(self):
        with self.assertRaises(ValueError):
            self.camera.text('redirect')
        self.assertNotIn('/never-follow', self.requests)


if __name__ == '__main__':
    unittest.main()
