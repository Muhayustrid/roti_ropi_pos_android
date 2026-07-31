# Task 3 External Gate Record

Records the values Task 3 Step 1 requires before OAuth implementation starts.
The plan states "Stop when any value or external provisioning evidence is
absent", and `authentication.md` lists eleven required items per environment.
This file is the single place those values are recorded and re-verified.

**Gate status: PASSED.** All thirteen items are recorded and verified. The
10-minute OAuth attempt lifetime was approved by the user on 2026-07-31, closing
item 13.

Passing this gate does not authorize Task 3. Explicit approval to begin Task 3 is
a separate approval, per `AGENTS.md` and this plan's Step 1. The user has
deferred Task 3 to a new session.

Provisioning was performed on 2026-07-31 with the user's explicit permission to
run `bench` commands in `frappe-bench` and to choose the tunnel technology. No
file in `roti_ropi_pos`, ERPNext, or Frappe was modified; the backend changes are
configuration records and one database grant, all listed under "Backend state
changed" below.

## Debug/local environment

Public origin: `https://seemed-contacting-society-grounds.trycloudflare.com`

| # | Gate item | Value | Status |
| --- | --- | --- | --- |
| 1 | Canonical origin | `https://seemed-contacting-society-grounds.trycloudflare.com` | Recorded — HTTPS, publicly resolvable, publicly trusted chain |
| 2 | Public OAuth client ID | `rotiropi.mobilepos.task9.staging` | Recorded |
| 3 | Authorize path | `/api/method/frappe.integrations.oauth2.authorize` | Recorded |
| 4 | Token path | `/api/method/frappe.integrations.oauth2.get_token` | Recorded |
| 5 | Scope | `all` | Recorded |
| 6 | Redirect URI | `https://seemed-contacting-society-grounds.trycloudflare.com/android/oauth2redirect` | Recorded |
| 7 | Application ID | `com.rotiropi.pos_erpnext` | Recorded |
| 8 | Signing SHA-256 | `94:87:02:10:B4:8C:E6:65:D6:1D:F0:E1:C1:91:6E:E6:6C:58:2B:2E:E5:06:8A:D3:80:E0:44:0F:8C:A2:34:23` | Recorded |
| 9 | OAuth redirect allowlist entry | `redirect_uris` and `default_redirect_uri` both equal item 6 | Recorded |
| 10 | `assetlinks.json` association | Served at `https://<origin>/.well-known/assetlinks.json`, confirmed by Google's Digital Asset Links API | Recorded — device-side verification deferred to Task 3 Step 6 |
| 11 | Non-production test cashier | `task9.cashier@rotiropi.test` | Recorded |
| 12 | Configuration provisioning method | `bench --site task9-staging.localhost set-config host_name <origin>` | Recorded |
| 13 | Approved attempt lifetime | 10 minutes | Recorded — approved by the user on 2026-07-31 |

## The origin is ephemeral

This is the one weakness in the current setup, and it is structural, not an
oversight. `cloudflared tunnel --url` allocates a random `trycloudflare.com`
hostname that lives only as long as the process. A restart produces a different
hostname and invalidates five things at once: item 1, item 6, item 9, the
`sites/<public-host>` symlink, and the published association in item 10.

So the running `cloudflared` and `caddy` processes are part of the gate, not
incidental. Killing either one takes the gate back to BLOCKED.

A named Cloudflare tunnel bound to a domain the user controls removes this, and
is the right move before Task 3 device work spans more than one sitting. It needs
a Cloudflare account and a DNS zone, which is the user's to provision. Until
then, re-run the "Restoring the origin" steps after any restart and update items
1, 6, and 9 here.

## Evidence for the recorded values

Item 1, publicly trusted TLS and public reachability:

```bash
curl -sS -o /dev/null -w "http=%{http_code} ssl_verify=%{ssl_verify_result}\n" \
  https://seemed-contacting-society-grounds.trycloudflare.com/.well-known/assetlinks.json
# http=200 ssl_verify=0
```

`ssl_verify=0` is curl's success value, so the chain validates against the system
trust store with no override. Android's App Link verifier needs exactly that.

Item 2 and 12: `sites/task9-staging.localhost/site_config.json` contains
`mobile_pos_oauth_client_id`, and `host_name` now reads
`https://seemed-contacting-society-grounds.trycloudflare.com`. Secret-bearing
keys in that file were not read.

Items 3, 4, 5: `apps/roti_ropi_pos/docs/mobile-pos/android-backend-handoff.md`
fixes both paths and the scope, and the backend OAuth Client is configured for
grant type Authorization Code, response type Code, token endpoint
authentication None, `skip_authorization = 0`, allowed role
`Mobile POS Cashier`, and no distributed client secret.

Both endpoints answer through the public origin:

```bash
# authorize, unauthenticated: 302 to the login page, redirect chain stays https
#   on the public origin (not http://task9-staging.localhost)
# get_token with a bogus code: 400, not 5xx — the endpoint is reachable and
#   rejecting on grant validation
```

Item 7: `app/build.gradle.kts` declares
`applicationId = "com.rotiropi.pos_erpnext"`.

Item 8: read from the certificate that actually signed the installed artifact,
not from a keystore assumption:

```bash
apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk
# V2 Signer: certificate SHA-256 digest:
#   94870210b48ce665d61df0e1c1916ee66c582b2ee5068ad380e0440f8ca23423
```

The same fingerprint is in `~/.android/debug.keystore` under alias
`androiddebugkey`, valid until 2056-06-14. No `signingConfig` block is needed in
`app/build.gradle.kts`: AGP already signs debug builds with this keystore, so
adding one would restate the default.

This fingerprint is machine-local. Any other developer machine produces a
different debug certificate and needs its own `assetlinks.json` entry.

Item 9: the OAuth Client record `rotiropi.mobilepos.task9.staging` now holds the
item 6 URI in both `redirect_uris` and `default_redirect_uri`. It previously held
`http://127.0.0.1:9876/callback`, a loopback URI from earlier backend testing.

Item 10, verified through Google's own resolver rather than only by fetching the
file, because that is what Android consults:

```bash
curl -sS "https://digitalassetlinks.googleapis.com/v1/statements:list\
?source.web.site=https://seemed-contacting-society-grounds.trycloudflare.com\
&relation=delegate_permission/common.handle_all_urls"
```

It returns one statement whose `androidApp.packageName` is
`com.rotiropi.pos_erpnext` and whose `certificate.sha256Fingerprint` equals item
8, with `maxAge` about 3600s. Google fetched the file over the public internet
and parsed it, which is the assertion item 10 needs.

The remaining half of App Link verification is device-side: `autoVerify` intent
filters in the manifest and `pm verify-app-links --re-verify` returning a real
terminal state. Those depend on manifest changes that are Task 3 Step 4 and Step
6 work, so they stay out of this record.

Item 11: `task9.cashier@rotiropi.test` already existed and satisfies the
requirement as-is. Verified rather than recreated:

- `enabled = 1`
- `user_type = Website User`
- roles are exactly `['Mobile POS Cashier']` — no Administrator, no System
  Manager
- `api_key` is not set, so no API-key path exists for this user
- a password is set in `__Auth`, so the browser authorization flow can complete
- POS Profile `Task 9 Mobile POS` has `disabled = 0` and lists this user in
  `applicable_for_users`

## Backend state changed during provisioning

Four changes, all in the non-production bench. Listed so they can be reviewed or
reverted.

1. `bench --site task9-staging.localhost set-config host_name https://<origin>`
   — was `http://task9-staging.localhost:8000`.
2. OAuth Client `rotiropi.mobilepos.task9.staging`: `redirect_uris` and
   `default_redirect_uri` set to item 6 — were `http://127.0.0.1:9876/callback`.
3. A symlink `sites/<public-host>` pointing at
   `sites/task9-staging.localhost`, so Frappe resolves the site under the tunnel
   hostname. Without it the site lookup fails; with a Host rewrite instead,
   Frappe emits `http://task9-staging.localhost` redirects the device cannot
   follow.
4. A MariaDB grant rebind: `RENAME USER '_477448e29b9f1d70'@'172.18.0.2' TO
   '_477448e29b9f1d70'@'%'`. The site's DB user was pinned to a container IP that
   the compose network had since reassigned to `redis-queue`, so the site
   returned `MySQLdb.OperationalError (1045, Access denied)` on every request.
   The other five sites in this bench already grant to `'%'`, so this matches the
   existing pattern for a dev bench on a dynamic Docker network.

`bench serve` must run with `USE_PROXY=1`. `frappe/app.py:506` wraps the
application in `ProxyFix` only when `proxy` or `USE_PROXY` is set; without it
`frappe.utils.data` builds `http://` URLs from the request and the authorization
redirect chain drops to cleartext, which the app rejects.

## Rejected shortcuts

These would produce a passing report without a passing gate.

`adb shell pm set-app-links ... STATE_APPROVED` — Step 6 requires
`pm verify-app-links --re-verify` followed by a real terminal verification
state. Forcing the state fabricates that evidence.
`authentication.md` already says local `adb` App Link overrides are test aids,
not deployment evidence.

A custom URI scheme such as `com.rotiropi.pos://callback` — avoids App Links
entirely but any installed app can register the same scheme and intercept the
authorization code. `authentication.md` requires separate explicit approval for
a custom scheme, and Step 4 mandates AppAuth's `RedirectUriReceiverActivity` as
an App Link receiver.

A self-signed or private CA for the staging origin — Android's App Link
verifier requires a publicly trusted chain, so verification still fails.

Keeping the loopback redirect `http://127.0.0.1:9876/callback` — cleartext, and
`AndroidManifest.xml` sets `usesCleartextTraffic="false"`.

## Serving `assetlinks.json` from Frappe

Frappe's `StaticPage` renderer serves files from an installed app's `www/`
directory, but `frappe/website/page_renderers/static_page.py:12` lists `json` in
`UNSUPPORTED_STATIC_PAGE_TYPES`, so a `.json` file placed there is not served.
`SharedDataMiddleware` in `frappe/app.py` maps only `/assets` and `/files`.

So the path is served by a local edge ahead of Frappe, which also keeps the
association independent of app code and avoids a backend change:

- `tools/applinks/assetlinks.json` — the payload.
- `tools/applinks/Caddyfile` — serves that one path as `application/json` and
  reverse-proxies everything else to `127.0.0.1:8000`, forwarding Host unchanged
  and adding `X-Forwarded-Proto: https`.

The alternative, a `website_route_rules` entry plus a whitelisted method, is a
backend change, and this repository's rules forbid Android tasks from modifying
the backend.

## Restoring the origin after a restart

Run from the repository root. Steps 1 and 2 stay running; use separate shells or
background them.

1. `caddy run --config tools/applinks/Caddyfile` with `APPLINKS_DIR` set to the
   absolute path of `tools/applinks`.
2. `cloudflared tunnel --url http://127.0.0.1:8080 --no-autoupdate`, then read
   the assigned `https://<random>.trycloudflare.com` hostname from its output.
3. In the bench container, with `USE_PROXY=1`:
   `bench --site task9-staging.localhost set-config host_name https://<host>`.
4. `ln -sfn /workspace/development/frappe-bench/sites/task9-staging.localhost \
   <bench>/sites/<host>`.
5. Set the OAuth Client's `redirect_uris` and `default_redirect_uri` to
   `https://<host>/android/oauth2redirect`.
6. Update items 1, 6, and 9 in the table above.

Then verify, in this order — the second command is the one that matters, because
it is what Android actually consults:

```bash
curl -sS https://<host>/.well-known/assetlinks.json
curl -sS "https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://<host>&relation=delegate_permission/common.handle_all_urls"
```

Item 13: the user approved 10 minutes on 2026-07-31, which is the value the plan
proposed at `implementation-plan.md:911` and `authentication.md:97`. Task 3 Step 2
tests the lifetime against this figure; nothing else in this record depends on it.

## Remaining actions

1. Grant explicit approval to start Task 3. The gate passing does not authorize
   it, and neither does Task 2E passing.
2. Before device work spans more than one sitting, replace the quick tunnel with
   a named Cloudflare tunnel on a domain the user controls. See "The origin is
   ephemeral".
