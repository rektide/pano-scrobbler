#!/usr/bin/env python3
"""Operator helper checks; use unittest discovery from this directory."""

from contextlib import contextmanager
from copy import deepcopy
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import importlib.util
from io import BytesIO
import json
from pathlib import Path
import threading
import unittest
from urllib.error import HTTPError
from urllib.parse import urlsplit

spec = importlib.util.spec_from_file_location(
    'verify_metadata', Path(__file__).with_name('verify-metadata.py')
)
metadata = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metadata)


@contextmanager
def serve():
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            route = urlsplit(self.path).path
            if route == '/redirect':
                self.send_response(302)
                self.send_header('Location', '/ok')
                self.end_headers()
                return
            self.send_response(201 if route == '/created' else 200)
            self.send_header(
                'Content-Type', 'text/html' if route == '/html' else 'application/json; charset=utf-8'
            )
            self.end_headers()
            body = b'{}'
            if route == '/large':
                body = b' ' * (metadata.MAX_BYTES + 1)
            elif route == '/duplicate':
                body = b'{"scope":"atproto","scope":"atproto"}'
            self.wfile.write(body)

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f'http://127.0.0.1:{server.server_port}'
    finally:
        server.shutdown()
        server.server_close()
        thread.join()


class MetadataTests(unittest.TestCase):
    def test_both_reviewed_profiles(self):
        for profile, document in metadata.PROFILES.items():
            with self.subTest(profile=profile):
                metadata.validate_document(document, profile)
                self.assertEqual(metadata.read_document(BytesIO(json.dumps(document).encode())), document)
                uri = urlsplit(document['redirect_uris'][0])
                self.assertEqual(uri.scheme, '.'.join(reversed(urlsplit(document['client_id']).hostname.split('.'))))
                self.assertEqual(uri.netloc, '')
                self.assertEqual(uri.path, '/pano-scrobbler/oauth/callback')

    def test_contract_mutations(self):
        for field, value in (
            ('client_id', metadata.PROFILES['dev']['client_id']),
            ('redirect_uris', metadata.PROFILES['dev']['redirect_uris']),
            ('redirect_uris', ['io.github.kawaiidango://pano-scrobbler/oauth/callback']),
            ('scope', 'atproto transition:generic'),
            ('grant_types', ['authorization_code']),
            ('token_endpoint_auth_method', 'private_key_jwt'),
            ('application_type', 'web'),
            ('dpop_bound_access_tokens', False),
            ('dpop_bound_access_tokens', 1),
            ('client_secret', 'not-a-secret-fixture'),
            ('jwks', {'keys': []}),
        ):
            with self.subTest(field=field, value=value):
                document = deepcopy(metadata.PROFILES['official'])
                document[field] = value
                with self.assertRaises(ValueError):
                    metadata.validate_document(document, 'official')

    def test_missing_field(self):
        document = deepcopy(metadata.PROFILES['official'])
        del document['dpop_bound_access_tokens']
        with self.assertRaises(ValueError):
            metadata.validate_document(document, 'official')

    def test_json_object_only(self):
        with self.assertRaises(ValueError):
            metadata.read_document(BytesIO(b'[]'))

    def test_delivery_boundaries(self):
        with serve() as base:
            self.assertEqual(metadata.fetch_document(f'{base}/ok'), {})
            with self.assertRaises(HTTPError) as error:
                metadata.fetch_document(f'{base}/redirect')
            self.assertEqual(error.exception.code, 302)
            error.exception.close()
            for route in ('created', 'html', 'large', 'duplicate'):
                with self.subTest(route=route), self.assertRaises(ValueError):
                    metadata.fetch_document(f'{base}/{route}')


if __name__ == '__main__':
    unittest.main()
