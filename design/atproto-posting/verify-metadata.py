#!/usr/bin/env python3
"""Check Pano's reviewed native OAuth contract locally or at its exact live URL.

This is a strict contribution-contract check, not a general OAuth validator.
The Pages contribution does not depend on this operator-side helper.
"""

import argparse
import json
from pathlib import Path
from urllib.request import HTTPRedirectHandler, Request, build_opener

MAX_BYTES = 64 * 1024
SCOPE = (
    'atproto repo:fm.teal.feed.play?action=create '
    'repo:app.rocksky.scrobble?action=create'
)
COMMON = {
    'scope': SCOPE,
    'grant_types': ['authorization_code', 'refresh_token'],
    'response_types': ['code'],
    'token_endpoint_auth_method': 'none',
    'application_type': 'native',
    'dpop_bound_access_tokens': True,
}
PROFILES = {
    'official': {
        **COMMON,
        'client_id': 'https://kawaiidango.github.io/pano-scrobbler/oauth-client-metadata.json',
        'client_name': 'Pano Scrobbler',
        'client_uri': 'https://kawaiidango.github.io/pano-scrobbler/',
        'redirect_uris': ['io.github.kawaiidango:/pano-scrobbler/oauth/callback'],
    },
    'dev': {
        **COMMON,
        'client_id': 'https://rektide.github.io/web-scrobbler-pages/pano-scrobbler/oauth-client-metadata.json',
        'client_name': 'Pano Scrobbler (rektide build)',
        'client_uri': 'https://rektide.github.io/web-scrobbler-pages/',
        'redirect_uris': ['io.github.rektide:/pano-scrobbler/oauth/callback'],
    },
}


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f'Duplicate JSON member: {key}')
        result[key] = value
    return result


def read_document(stream):
    body = stream.read(MAX_BYTES + 1)
    if len(body) > MAX_BYTES:
        raise ValueError('Metadata exceeds the 64 KiB verification limit')
    document = json.loads(body.decode('utf-8'), object_pairs_hook=unique_keys)
    if not isinstance(document, dict):
        raise ValueError('Metadata must be a JSON object')
    return document


def fetch_document(url):
    request = Request(url, headers={'Accept': 'application/json'})
    with build_opener(NoRedirect()).open(request, timeout=20) as response:
        if response.status != 200:
            raise ValueError(f'Metadata requires HTTP 200, received {response.status}')
        if response.geturl() != url:
            raise ValueError('Metadata URL changed during fetch')
        if response.headers.get_content_type() != 'application/json':
            raise ValueError('Metadata requires Content-Type: application/json')
        return read_document(response)


def validate_document(document, profile):
    expected = PROFILES[profile]
    if set(document) != set(expected):
        raise ValueError('Metadata fields differ from the reviewed public-client contract')
    # JSON true and 1 compare equal in Python; enforce types as well as values.
    for field, value in expected.items():
        if type(document[field]) is not type(value) or document[field] != value:
            raise ValueError(f'Metadata contract mismatch: {field}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument('--file', type=Path, help='Check source or built artifact JSON')
    source.add_argument('--live', action='store_true', help='GET the selected exact client_id')
    parser.add_argument('--profile', choices=PROFILES, default='official')
    args = parser.parse_args()
    if args.file is not None:
        with args.file.open('rb') as stream:
            document = read_document(stream)
    else:
        document = fetch_document(PROFILES[args.profile]['client_id'])
    validate_document(document, args.profile)
    print(f'PASS: {args.profile} public native OAuth metadata contract')


if __name__ == '__main__':
    main()
