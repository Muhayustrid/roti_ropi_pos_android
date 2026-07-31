# Session Handoff: Task 3 Starting Point

Written 2026-07-31 at the end of the session that provisioned the Task 3 external
gate. Read this together with `task-3-gate-record.md`; this file covers session
and environment state, that file is the gate evidence itself.

Nothing in Task 3 has been implemented. No OAuth source, no AppAuth dependency,
no manifest intent filters.

## Task status

| Task | Status |
| --- | --- |
| 1A, 1B | Completed |
| 2 | Completed — commit `f49f624` |
| 2B | Completed |
| 2C | Completed — `3801d03`, merged `3352ddd` |
| 2D | Completed — `ee56e73`, merged `ef491fd` |
| 2E | Completed — candidate `a391411`, merged as `cb229ba` (PR #5, squash) |
| 3 | Not Started — external gate PASSED, start approval NOT given |
| 4–12 | Not Started, straight dependency chain behind Task 3 |

Task 2E's evidence line in `implementation-plan.md:39` is unchanged and still
reads Completed: clean Gradle gate 100 tasks, API 23 and API 36 each 41 tests.

## Git state

- Worktree: `/Users/rotiropi/DockerERPNext/POSERPNext/.claude/worktrees/task-2e-api23-fix` (locked)
- Branch `docs/task-3-gate-record`. First commit `b0d04c5` recorded twelve gate
  items; a second commit closes item 13 and adds this handoff. Both are pushed.
- Main checkout `/Users/rotiropi/DockerERPNext/POSERPNext` on `main` @ `cb229ba`
- Draft PR #6, base `main`, head = this branch — open, not merged, docs and
  tooling only. Five files: the three docs below plus `tools/applinks/Caddyfile`
  and `tools/applinks/assetlinks.json`.

Committed on this branch, awaiting PR #6 review:

- `docs/mobile-pos/task-3-gate-record.md` — item 13 set to 10 minutes, gate status PASSED
- `docs/mobile-pos/implementation-plan.md` — Step 1 pointer paragraph updated to match
- `docs/mobile-pos/session-handoff-task-3.md` — this file

The worktree is otherwise clean. No Android source file was touched on this
branch.

Uncommitted in the main checkout, unrelated and intentional: `.idea/misc.xml`
modified; `.claude/`, `.idea/markdown.xml`, `preview.png` untracked.

Remote branches: `main`, `docs/task-3-gate-record`, `docs/pending-doc-updates`,
`feat/compose-pos-foundation`, `feat/dashboard-products-ui`.
`docs/pending-doc-updates` is an archive branch — it carries a pre-merge
`ReportsScreen.kt` with the chart alignment bug already fixed on `main`, so do not
merge it wholesale. `AGENTS.md` and `docs/mobile-pos/ui-ux-reference-design.md`
there are per-file cherry-pick candidates.

## The staging origin is ephemeral — read this first

`https://seemed-contacting-society-grounds.trycloudflare.com`

This hostname is allocated by `cloudflared tunnel --url` and lives only as long
as that process. It is almost certainly dead by the time the new session starts.
When it dies, five things break at once: gate items 1, 6, 9, the
`sites/<public-host>` symlink, and the published `assetlinks.json` association.

So the first thing the new session should do is check whether the origin is
alive, and restore it if not. Do not assume the value above still resolves.

Two host processes were running at the end of this session and are expected to be
gone:

| Process | PID | Uptime at handoff |
| --- | --- | --- |
| `cloudflared tunnel --url http://127.0.0.1:8080 --no-autoupdate` | 67085 | 41 min |
| `caddy run --config tools/applinks/Caddyfile` | 68526 | 36 min |

Plus, inside the devcontainer, `bench serve --port 8000` (PIDs 9017/9022) and
four containers `frappe_docker_devcontainer-{frappe,mariadb,redis-cache,redis-queue}-1`.

Recommendation carried over: before Task 3 device work spans more than one
sitting, replace the quick tunnel with a named Cloudflare tunnel on a domain the
user controls. That removes this whole failure class. It needs a Cloudflare
account and a DNS zone, which is the user's to provision.

## Checking whether the origin is alive

```bash
H=https://seemed-contacting-society-grounds.trycloudflare.com
curl -sS -o /dev/null -w "assetlinks http=%{http_code} type=%{content_type} ssl_verify=%{ssl_verify_result}\n" --max-time 20 "$H/.well-known/assetlinks.json"
curl -sS -o /dev/null -w "ping       http=%{http_code}\n" --max-time 20 "$H/api/method/ping"
```

Healthy looks like `http=200 type=application/json ssl_verify=0` and `http=200`.
`ssl_verify=0` is curl's success value; it is what proves the publicly trusted
chain Android's App Link verifier requires. Anything else means restore.

## Restoring the origin

Six steps, in order. Steps 2 and 3 stay running, so background them or use
separate shells. `<bench>` is
`/Users/rotiropi/DockerERPNext/frappe_docker/development/frappe-bench`.

1. Start the devcontainer stack if the containers are down. Start the data
   services first, wait a few seconds, then the app container:

   ```bash
   docker start frappe_docker_devcontainer-mariadb-1 \
     frappe_docker_devcontainer-redis-cache-1 frappe_docker_devcontainer-redis-queue-1
   sleep 3
   docker start frappe_docker_devcontainer-frappe-1
   ```

2. Start `bench serve` **with `USE_PROXY=1`**. This flag is not optional; see
   "Why USE_PROXY=1" below.

   ```bash
   docker exec -d frappe_docker_devcontainer-frappe-1 bash -lc \
     'cd /workspace/development/frappe-bench && USE_PROXY=1 nohup bench serve --port 8000 > /tmp/bench-serve.log 2>&1'
   ```

3. Start the local edge, from the repository root:

   ```bash
   export APPLINKS_DIR="$PWD/tools/applinks"
   caddy run --config tools/applinks/Caddyfile
   ```

4. Start the tunnel and read the new hostname from its output:

   ```bash
   cloudflared tunnel --url http://127.0.0.1:8080 --no-autoupdate
   ```

5. Point the site at the new origin, and make Frappe resolve the site under that
   hostname:

   ```bash
   docker exec frappe_docker_devcontainer-frappe-1 bash -lc \
     "cd /workspace/development/frappe-bench && bench --site task9-staging.localhost set-config host_name 'https://<host>'"
   ln -sfn /workspace/development/frappe-bench/sites/task9-staging.localhost "<bench>/sites/<host>"
   ```

6. Update the OAuth Client redirect fields to the new origin:

   ```bash
   R="https://<host>/android/oauth2redirect"
   docker exec frappe_docker_devcontainer-frappe-1 bash -lc \
     "cd /workspace/development/frappe-bench && bench --site task9-staging.localhost execute frappe.client.set_value --kwargs '{\"doctype\":\"OAuth Client\",\"name\":\"rotiropi.mobilepos.task9.staging\",\"fieldname\":{\"redirect_uris\":\"$R\",\"default_redirect_uri\":\"$R\"}}'"
   ```

Then update items 1, 6, and 9 in `task-3-gate-record.md`, and verify:

```bash
curl -sS "https://<host>/.well-known/assetlinks.json"
curl -sS "https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://<host>&relation=delegate_permission/common.handle_all_urls"
```

The second command is the one that matters — it is what Android actually
consults. It must return one statement with `packageName`
`com.rotiropi.pos_erpnext` and the item 8 fingerprint. Google caches for about an
hour (`maxAge` ≈ 3600s), so a fresh hostname resolves quickly but a changed file
on an existing hostname may not.

## The moving parts, and why each exists

`tools/applinks/Caddyfile` — listens on `127.0.0.1:8080`. Serves exactly
`/.well-known/assetlinks.json` as `application/json` from `$APPLINKS_DIR`, and
reverse-proxies everything else to `127.0.0.1:8000` with `X-Forwarded-Proto:
https` added and Host forwarded unchanged. `auto_https off`, `admin off` — TLS is
terminated at the Cloudflare edge, so this listener is plain HTTP on loopback and
unreachable from outside the machine. Because `admin off`, `caddy reload` does not
work; stop and start it instead.

`tools/applinks/assetlinks.json` — the association payload: relation
`delegate_permission/common.handle_all_urls`, package
`com.rotiropi.pos_erpnext`, and the debug signing fingerprint.

Frappe cannot serve this file itself.
`frappe/website/page_renderers/static_page.py:12` lists `json` in
`UNSUPPORTED_STATIC_PAGE_TYPES`, and `frappe/app.py` maps `SharedDataMiddleware`
only to `/assets` and `/files`. The alternative — `website_route_rules` plus a
whitelisted method — is a backend change, and Android tasks may not modify the
backend. Hence the local edge.

### Why USE_PROXY=1

`frappe/app.py:506` wraps the application in `ProxyFix` only when the `proxy`
flag or the `USE_PROXY` environment variable is set. Without it,
`frappe/utils/data.py:1900` builds `http://` URLs from the request, and the
authorization redirect chain drops to cleartext. `AndroidManifest.xml` sets
`android:usesCleartextTraffic="false"`, so the app rejects that. With the flag,
the whole 302 chain stays `https` on the public origin.

### Why the hostname symlink

`sites/<public-host>` → `sites/task9-staging.localhost`. Frappe resolves the site
from the Host header, so the tunnel hostname needs a matching entry. The
alternative — rewriting Host to `task9-staging.localhost` at the proxy — was tried
and rejected: Frappe then emits `http://task9-staging.localhost` redirects the
device cannot follow.

## Backend state changed during provisioning

Four changes, all in the non-production bench. Listed so they can be reviewed or
reverted.

| # | Change | Previous value |
| --- | --- | --- |
| 1 | `host_name` on site `task9-staging.localhost` | `http://task9-staging.localhost:8000` |
| 2 | OAuth Client `rotiropi.mobilepos.task9.staging`: `redirect_uris` and `default_redirect_uri` | `http://127.0.0.1:9876/callback` |
| 3 | Symlink `sites/<public-host>` → `sites/task9-staging.localhost` | did not exist |
| 4 | MariaDB: `RENAME USER '_477448e29b9f1d70'@'172.18.0.2' TO '_477448e29b9f1d70'@'%'` | grant pinned to `172.18.0.2` |

Change 4 needs context. The site's DB user was pinned to a container IP that the
compose network had since reassigned to `redis-queue`, so every request to
`task9-staging.localhost` returned `MySQLdb.OperationalError (1045, Access
denied)`. The site was already broken before this session touched it. The other
five sites in this bench already grant to `'%'`, so this matches the existing
pattern for a dev bench on a dynamic Docker network.

## OAuth Client and test cashier

OAuth Client `rotiropi.mobilepos.task9.staging`:

- `grant_type` Authorization Code, `response_type` Code
- `token_endpoint_auth_method` None — public client, no secret distributed to the app
- `skip_authorization` 0
- `scopes` `all`
- `allowed_roles` exactly `Mobile POS Cashier`
- redirect: `https://<origin>/android/oauth2redirect` in both fields

A second OAuth Client `j0s70gr7qe` exists with the same `app_name` and still
carries the old loopback redirect. Task 3 must use
`rotiropi.mobilepos.task9.staging`, the one named in the site config under
`mobile_pos_oauth_client_id`.

Test cashier `task9.cashier@rotiropi.test` — verified, not created:

- `enabled = 1`, `user_type = Website User`
- roles exactly `['Mobile POS Cashier']` — no Administrator, no System Manager
- `api_key` not set, so no API-key path exists for this user
- password set in `__Auth`, so browser authorization can complete
- POS Profile `Task 9 Mobile POS`: `disabled = 0`, lists this user in
  `applicable_for_users`, company `Task 9 Staging Company`, payment `Cash`

## Task 3 external gate — 13/13, and the evidence

`task-3-gate-record.md` is the record; this is the summary. All thirteen items
are Recorded, none BLOCKED. Gate status PASSED. Item 13, the 10-minute OAuth
attempt lifetime, was approved by the user on 2026-07-31.

The evidence that is not just "a value was written down":

- Item 1 — `curl -o /dev/null -w '%{ssl_verify_result}'` on the origin returned
  `http=200 ssl_verify=0`. `ssl_verify=0` is curl's success value, so the chain
  validates against the system trust store with no override. Android's App Link
  verifier requires exactly that.
- Item 8 — read from the certificate that actually signed the installed artifact:
  `apksigner verify --print-certs app/build/outputs/apk/debug/app-debug.apk` →
  `94870210b48ce665d61df0e1c1916ee66c582b2ee5068ad380e0440f8ca23423`. Same
  fingerprint in `~/.android/debug.keystore` alias `androiddebugkey`, valid until
  2056-06-14. No `signingConfig` block is needed in `app/build.gradle.kts` — AGP
  already signs debug builds with this keystore. **This fingerprint is
  machine-local**; another developer machine needs its own `assetlinks.json`
  entry.
- Item 10 — verified through Google's resolver, not only by fetching the file,
  because the resolver is what Android consults. It returned one statement with
  `androidApp.packageName` `com.rotiropi.pos_erpnext` and
  `certificate.sha256Fingerprint` equal to item 8, `maxAge` ≈ 3600s.
- Items 3, 4 — both endpoints answer through the public origin. Authorize
  unauthenticated: 302 to the login page, chain staying `https` on the public
  origin. `get_token` with a bogus code: 400, not 5xx — reachable and rejecting on
  grant validation.
- `/android/oauth2redirect` currently returns 404. That is expected and not a
  gate failure: nothing receives that path until AppAuth's
  `RedirectUriReceiverActivity` is added in Task 3 Step 4.

The device half of App Link verification is deliberately **not** in the record.
`autoVerify` intent filters and `pm verify-app-links --re-verify` returning a real
terminal state depend on manifest changes that are Task 3 Step 4 and Step 6 work.

## What to read, in this order

1. `AGENTS.md` — the rules that bind every task. Approval gates, Indonesian for
   user-facing communication and English for repository content, the OAuth
   security rules, and the ban on touching the backend from an Android task.
2. `docs/mobile-pos/task-3-gate-record.md` — the gate itself and its evidence.
3. This file — session and environment state.
4. `docs/mobile-pos/implementation-plan.md:841-1027` — Task 3's seven steps.
   Task 4 begins at `:1028`; do not read ahead into it as work.
5. `docs/mobile-pos/authentication.md` — the OAuth contract. `:97` for the
   attempt lifetime, ~`:329` for the approval requirements, ~`:357` for the abort
   conditions.
6. `docs/mobile-pos/testing-strategy.md` — how the red tests in Step 2 and Step 3
   are expected to be written and run.
7. `tools/applinks/Caddyfile` and `tools/applinks/assetlinks.json` — small, and
   both carry explanatory comments.

## Risks, and what not to touch

**The ephemeral origin is the top risk.** Everything above assumes it. Check it
first, restore it before anything else, and update items 1, 6, 9 when it changes.

**Do not merge `docs/pending-doc-updates` wholesale.** It carries a pre-merge
`ReportsScreen.kt` with the chart alignment bug that is already fixed on `main`.
`AGENTS.md` and `docs/mobile-pos/ui-ux-reference-design.md` there are per-file
cherry-pick candidates; the rest is stale.

**Do not modify the backend.** `roti_ropi_pos`, `bakery_manufacturing`, ERPNext,
Frappe. What was changed during provisioning is configuration records and one
database grant, all listed above, under the user's explicit permission — no
backend source file was touched, and Task 3 needs none.

**Leave the unrelated dirt alone.** In the main checkout: `.idea/misc.xml`
modified, `.claude/`, `.idea/markdown.xml`, `preview.png` untracked. Not this
work's.

**Do not merge PR #6 yourself.** It is a draft awaiting the user's review. Task 3
starts after they merge it, not before.

### Four shortcuts that produce a passing report without a passing gate

1. `adb shell pm set-app-links ... STATE_APPROVED`. Step 6 requires
   `pm verify-app-links --re-verify` followed by a real terminal verification
   state; forcing the state fabricates that evidence. `authentication.md` already
   says local `adb` App Link overrides are test aids, not deployment evidence.
2. A custom URI scheme such as `com.rotiropi.pos://callback`. Any installed app can
   register the same scheme and intercept the authorization code. This needs
   separate explicit approval, and Step 4 mandates AppAuth's
   `RedirectUriReceiverActivity` as an App Link receiver.
3. A self-signed or private CA for the staging origin. Android's verifier requires
   a publicly trusted chain, so verification still fails.
4. Keeping a loopback redirect like `http://127.0.0.1:9876/callback`. Cleartext,
   and `AndroidManifest.xml` sets `usesCleartextTraffic="false"`.

## Exact starting point for Task 3

Two things must be true before any Task 3 code is written:

1. **The user grants explicit approval to begin Task 3.** Not given as of this
   handoff. The gate passing does not authorize the task, and neither does Task 2E
   passing — `AGENTS.md:9` and the plan's Step 1 keep these separate.
2. **The origin is alive**, per "Checking whether the origin is alive" above.

With both true, Task 3 Step 1 is already satisfied by the gate record — reread it
to confirm, do not re-provision. Work begins at Step 2, "Write failing OAuth and
Keystore tests", at `implementation-plan.md`. Step 3 runs those tests red before
Step 4 adds AppAuth and the Android Keystore implementation.

Nothing exists yet: no OAuth source file, no AppAuth dependency in
`app/build.gradle.kts`, no intent filter in `AndroidManifest.xml`. The first diff
of Task 3 should be tests only.

Values Task 3 needs are all in the gate record table — client ID
`rotiropi.mobilepos.task9.staging`, authorize
`/api/method/frappe.integrations.oauth2.authorize`, token
`/api/method/frappe.integrations.oauth2.get_token`, scope `all`, redirect
`https://<origin>/android/oauth2redirect`, attempt lifetime 10 minutes. Read them
from the record rather than from this file, since the origin-bearing ones change
with every tunnel restart.

Task 4 stays untouched until Task 3 is approved and passing.
