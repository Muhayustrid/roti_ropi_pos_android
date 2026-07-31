# Task 3 External Gate Record

Records the values Task 3 Step 1 requires before OAuth implementation starts.
The plan states "Stop when any value or external provisioning evidence is
absent", and `authentication.md` lists eleven required items per environment.
This file is the single place those values are recorded and re-verified.

Android does not provision the OAuth client, the cashier user, or the
`assetlinks.json` deployment. Those are backend and infrastructure actions.

**Gate status: BLOCKED.** Seven of thirteen items are recorded; six are absent.
Task 3 implementation must not start.

## Debug/local environment

| # | Gate item | Value | Status |
| --- | --- | --- | --- |
| 1 | Canonical origin | `http://task9-staging.localhost:8000` | **BLOCKED** — cleartext, and `.localhost` is not publicly resolvable |
| 2 | Public OAuth client ID | `rotiropi.mobilepos.task9.staging` | Recorded |
| 3 | Authorize path | `/api/method/frappe.integrations.oauth2.authorize` | Recorded |
| 4 | Token path | `/api/method/frappe.integrations.oauth2.get_token` | Recorded |
| 5 | Scope | `all` | Recorded |
| 6 | Redirect URI | — | **BLOCKED** — none approved |
| 7 | Application ID | `com.rotiropi.pos_erpnext` | Recorded |
| 8 | Signing SHA-256 | `94:87:02:10:B4:8C:E6:65:D6:1D:F0:E1:C1:91:6E:E6:6C:58:2B:2E:E5:06:8A:D3:80:E0:44:0F:8C:A2:34:23` | Recorded |
| 9 | OAuth redirect allowlist entry | — | **BLOCKED** — depends on 6 |
| 10 | `assetlinks.json` association | — | **BLOCKED** — not deployed anywhere |
| 11 | Non-production test cashier | — | **BLOCKED** — not created |
| 12 | Configuration provisioning method | `bench --site <site> set-config mobile_pos_oauth_client_id <client-id>` | Recorded |
| 13 | Approved attempt lifetime | — | **BLOCKED** — 10-minute proposal not approved |

## Evidence for the recorded values

Item 2 and 12: `sites/task9-staging.localhost/site_config.json` contains
`mobile_pos_oauth_client_id`. Secret-bearing keys in that file were not read.

Items 3, 4, 5: `apps/roti_ropi_pos/docs/mobile-pos/android-backend-handoff.md`
fixes both paths and the scope, and the backend OAuth Client is configured for
grant type Authorization Code, response type Code, token endpoint
authentication None, `skip_authorization = 0`, allowed role
`Mobile POS Cashier`, and no distributed client secret.

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

## Why item 1 blocks items 6, 9, 10, and 11

App Link verification is not a local check. Android fetches
`https://<host>/.well-known/assetlinks.json` from the public internet over TLS
with a valid certificate chain. `task9-staging.localhost` fails on both counts:
the host does not resolve outside this machine, and the site serves plain HTTP.

`AGENTS.md` requires HTTPS only and rejects cleartext endpoints.
`app/src/main/AndroidManifest.xml` sets `android:usesCleartextTraffic="false"`,
so the current origin cannot be reached by the app even if verification were
skipped. `authentication.md` states "No HTTP exception is currently approved"
for the debug environment.

Adding TLS to the local bench alone does not fix this. A publicly resolvable
hostname is required, so a stable-hostname tunnel to the bench is the cheapest
path. Once that origin exists, items 6, 9, 10, and 11 become straightforward.

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

## Serving `assetlinks.json` from Frappe

Frappe's `StaticPage` renderer serves files from an installed app's `www/`
directory, but `frappe/website/page_renderers/static_page.py` lists `json` in
`UNSUPPORTED_STATIC_PAGE_TYPES`, so a `.json` file placed there is not served.
`SharedDataMiddleware` in `frappe/app.py` maps only `/assets` and `/files`.

So `/.well-known/assetlinks.json` needs one of:

- a reverse proxy or tunnel rule serving the path directly, ahead of Frappe —
  preferred, because it keeps the association independent of app code; or
- a Frappe `website_route_rules` entry plus a whitelisted method returning the
  JSON with `Content-Type: application/json`; a backend change, and this
  repository's rules forbid Android tasks from modifying the backend.

## Remaining actions, in order

1. Stand up a tunnel with a stable public hostname to the bench on port 8000,
   with a publicly trusted TLS certificate.
2. `bench --site task9-staging.localhost set-config host_name https://<host>`.
3. Choose the redirect URI, for example `https://<host>/android/oauth2redirect`,
   and record it here as item 6.
4. Add that exact URI to the backend OAuth Client redirect allowlist (item 9).
5. Serve `/.well-known/assetlinks.json` on that host with the package name and
   the item 8 fingerprint (item 10):

   ```json
   [{
     "relation": ["delegate_permission/common.handle_all_urls"],
     "target": {
       "namespace": "android_app",
       "package_name": "com.rotiropi.pos_erpnext",
       "sha256_cert_fingerprints": [
         "94:87:02:10:B4:8C:E6:65:D6:1D:F0:E1:C1:91:6E:E6:6C:58:2B:2E:E5:06:8A:D3:80:E0:44:0F:8C:A2:34:23"
       ]
     }
   }]
   ```

6. Create a non-production cashier user holding only the `Mobile POS Cashier`
   role, assigned to an enabled POS Profile (item 11).
7. Approve an OAuth attempt lifetime (item 13). The plan proposes 10 minutes but
   marks it unapproved.
8. Grant explicit approval to start Task 3. Task 2E passing does not authorize
   it.

Verify item 10 before implementation with a plain fetch from a device network:

```bash
curl -sS https://<host>/.well-known/assetlinks.json
```

