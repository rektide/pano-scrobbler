"""Install and check Pano's public native client document in a Pages artifact."""

import json
import pathlib
import shutil
import sys

source = pathlib.Path("oauth-client-metadata.json")
artifact = pathlib.Path(sys.argv[1]) / source.name
shutil.copyfile(source, artifact)
doc = json.loads(artifact.read_bytes())
assert doc.keys() == {
    "client_id", "client_name", "client_uri", "application_type", "redirect_uris",
    "grant_types", "response_types", "token_endpoint_auth_method",
    "dpop_bound_access_tokens", "scope",
}
assert doc["client_id"] == "https://kawaiidango.github.io/pano-scrobbler/oauth-client-metadata.json"
assert doc["redirect_uris"] == ["io.github.kawaiidango:/pano-scrobbler/oauth/callback"]
assert doc["application_type"] == "native"
assert doc["grant_types"] == ["authorization_code", "refresh_token"]
assert doc["response_types"] == ["code"]
assert doc["token_endpoint_auth_method"] == "none"
assert doc["dpop_bound_access_tokens"] is True
assert doc["scope"].split() == [
    "atproto",
    "repo:fm.teal.feed.play?action=create&action=delete",
    "repo:app.rocksky.scrobble?action=create&action=delete",
    "repo:fm.teal.actor.status?action=create&action=update&action=delete",
    "repo:app.rocksky.actor.status?action=create&action=update&action=delete",
    "repo:app.rocksky.like?action=create&action=delete",
]
