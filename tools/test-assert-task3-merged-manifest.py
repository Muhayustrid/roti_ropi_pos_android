#!/usr/bin/env python3
import importlib.util
import subprocess
import sys
import tempfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("assert-task3-merged-manifest.py")
ANDROID = "http://schemas.android.com/apk/res/android"
HOST = "oauth-staging.rotiropi.web.id"
PATH = "/android/oauth2redirect"


def component(name, exported, data, tag="activity"):
    return f'''<{tag} android:name="{name}" android:exported="{exported}">{data}</{tag}>'''


def alias(name, target, exported, data):
    return (
        f'''<activity-alias android:name="{name}" android:targetActivity="{target}" '''
        f'''android:exported="{exported}">{data}</activity-alias>'''
    )


def intent(data):
    return f"<intent-filter>{data}</intent-filter>"


def data(**attrs):
    return "<data " + " ".join(f'android:{key}="{value}"' for key, value in attrs.items()) + "/>"


def manifest(extra):
    approved = component(
        "net.openid.appauth.RedirectUriReceiverActivity", "true",
        intent(data(scheme="https", host=HOST, path=PATH)),
    )
    completion = component(".auth.AuthCompletionActivity", "false", "")
    return f'''<manifest xmlns:android="{ANDROID}"><application>{approved}{completion}{extra}</application></manifest>'''


def run(xml):
    with tempfile.NamedTemporaryFile("w", suffix=".xml") as fixture:
        fixture.write(xml)
        fixture.flush()
        return subprocess.run([sys.executable, str(SCRIPT), fixture.name], capture_output=True, text=True)


if __name__ == "__main__":
    assert run(manifest("")).returncode == 0
    fixtures = {
        "scheme-only HTTPS": component(".MainActivity", "true", intent(data(scheme="https"))),
        "wildcard HTTPS host": component(".MainActivity", "true", intent(data(scheme="https", host="*.trycloudflare.com", path=PATH))),
        "inline wildcard HTTPS host": component(".MainActivity", "true", intent(data(scheme="https", host="callback*.trycloudflare.com", path=PATH))),
        "approved host no path": component(".MainActivity", "true", intent(data(scheme="https", host=HOST))),
        "broad pathPrefix": component(".MainActivity", "true", intent(data(scheme="https", host=HOST, pathPrefix="/android"))),
        "callback on MainActivity": component(".MainActivity", "true", intent(data(scheme="https", host=HOST, path=PATH))),
        "callback another exported activity": component(".OtherActivity", "true", intent(data(scheme="https", host=HOST, path=PATH))),
        "custom oauth scheme": component(".MainActivity", "true", intent(data(scheme="oauth2redirect"))),
        "callback activity alias": alias(".CallbackAlias", ".MainActivity", "true", intent(data(scheme="https", host=HOST, path=PATH))),
        "duplicate exact receiver": component("net.openid.appauth.RedirectUriReceiverActivity", "true", intent(data(scheme="https", host=HOST, path=PATH))),
    }
    for name, extra in fixtures.items():
        result = run(manifest(extra))
        assert result.returncode != 0, f"fixture accepted: {name}"
    print(f"PASS: {len(fixtures)} negative manifest fixtures rejected")
