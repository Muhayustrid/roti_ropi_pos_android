#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
APP_ID = "com.rotiropi.pos_erpnext"
CALLBACK = "net.openid.appauth.RedirectUriReceiverActivity"
COMPLETION = f"{APP_ID}.auth.AuthCompletionActivity"
EXPECTED_DATA = (
    "https",
    "oauth-staging.rotiropi.web.id",
    "/android/oauth2redirect",
)


def component_name(item):
    name = item.get(ANDROID + "name")
    if name is None:
        return None
    if name.startswith("."):
        return APP_ID + name
    return name


def exact_callback(data):
    return (
        data.get(ANDROID + "scheme"),
        data.get(ANDROID + "host"),
        data.get(ANDROID + "path"),
    ) == EXPECTED_DATA and all(
        data.get(ANDROID + attr) is None
        for attr in ("pathPrefix", "pathPattern", "pathAdvancedPattern", "port")
    )


def filter_can_match_callback(intent_filter):
    # Android combines data attributes from every <data> declaration in one filter.
    # Treat patterns as matching when overlap cannot be proven impossible.
    declarations = intent_filter.findall("data")
    schemes = {data.get(ANDROID + "scheme") for data in declarations}
    if "oauth2redirect" in schemes:
        return True
    if "https" not in schemes:
        return False

    hosts = {data.get(ANDROID + "host") for data in declarations} - {None}
    if not hosts or any("*" in host for host in hosts):
        host_matches = True
    else:
        host_matches = EXPECTED_DATA[1] in hosts
    if not host_matches:
        return False

    paths = {data.get(ANDROID + "path") for data in declarations} - {None}
    has_pattern = any(
        data.get(ANDROID + attr) is not None
        for data in declarations
        for attr in ("pathPrefix", "pathPattern", "pathAdvancedPattern")
    )
    return not paths or EXPECTED_DATA[2] in paths or has_pattern


def exact_filter(intent_filter):
    declarations = intent_filter.findall("data")
    return len(declarations) == 1 and exact_callback(declarations[0])


def assert_manifest(root):
    components = root.findall("./application/*")  # Includes activity aliases and every exported component.
    by_name = {component_name(item): item for item in components}
    callback = by_name.get(CALLBACK)
    completion = by_name.get(COMPLETION)
    assert callback is not None, "missing AppAuth HTTPS callback receiver"
    assert callback.get(ANDROID + "exported") == "true", "callback receiver must be exported"
    assert completion is not None, "missing completion activity"
    assert completion.get(ANDROID + "exported") == "false", "completion activity must not be exported"

    approved = 0
    for component in components:
        name = component_name(component)
        exported = component.get(ANDROID + "exported") == "true"
        for intent_filter in component.findall("intent-filter"):
            if exact_filter(intent_filter):
                approved += 1
            if filter_can_match_callback(intent_filter):
                assert name == CALLBACK and exported, (
                    f"unintended OAuth callback component: {name}"
                )
                assert exact_filter(intent_filter), (
                    "approved callback filter must be one exact HTTPS data declaration"
                )

    assert approved == 1, f"expected one exact AppAuth callback filter, found {approved}"


if len(sys.argv) != 2:
    raise SystemExit("usage: assert-task3-merged-manifest.py <merged AndroidManifest.xml>")

assert_manifest(ET.parse(sys.argv[1]).getroot())
print("PASS: exact HTTPS AppAuth callback only; completion activity non-exported")
