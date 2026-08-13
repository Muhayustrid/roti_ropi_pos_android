# Task 9 mobile-pos-response-drop/v1 Staging Runbook

- **Protocol:** `mobile-pos-response-drop/v1`
- **Backend commit:** `2b0ee79e5644d4b67b607c9627b4b2ba75260856`
- **Target environment:** Clean Linux (preferred) or macOS host with Docker, Python 3, curl, jq, git
- **Estimated duration:** ~90 minutes on clean host with Docker already installed
- **Operator:** Single operator with root/sudo

---

## Section 1: Prerequisites

### 1.1 Required software

```bash
# Verify or install each. Minimum versions:
docker --version          # ≥ 27.0
python3 --version         # ≥ 3.10 (stdlib only for proxy/test_proxy — no pip deps)
curl --version            # ≥ 8.0
jq --version              # ≥ 1.6
git --version             # ≥ 2.40
caddy version             # ≥ 2.7
cloudflared version       # ≥ 2025.2
```

On Debian/Ubuntu:
```bash
sudo apt-get update && sudo apt-get install -y docker.io python3 curl jq git
# Caddy: https://caddyserver.com/docs/install#debian-ubuntu-raspbian
# cloudflared: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/
```

### 1.2 Files to copy from current host

Copy these 6 files into a staging directory on the target host (e.g. `{{STAGING_DIR}}`):

**Proxy tooling** (from `/Users/rotiropi/DockerERPNext/.orchestrator/tools/mobile-pos-response-drop/`):
```
proxy.py           → {{STAGING_DIR}}/tools/proxy.py
test_proxy.py      → {{STAGING_DIR}}/tools/test_proxy.py
activate.sh        → {{STAGING_DIR}}/tools/activate.sh       (needs patching — see 1.3)
deactivate.sh      → {{STAGING_DIR}}/tools/deactivate.sh     (needs patching — see 1.3)
```

**Ingress assets** (from `/Users/rotiropi/DockerERPNext/POSERPNext/tools/applinks/`):
```
Caddyfile          → {{STAGING_DIR}}/tools/Caddyfile
assetlinks.json    → {{STAGING_DIR}}/tools/assetlinks.json
```

**Docker Compose** (from `/Users/rotiropi/DockerERPNext/frappe_docker/pwd.yml`):
```
pwd.yml            → {{STAGING_DIR}}/pwd.yml
```

### 1.3 Portability fixes — apply before use

Activate.sh and deactivate.sh have two portability issues:
1. Hardcoded macOS user path `/Users/rotiropi/caddy-rotiropi/Caddyfile`
2. macOS-only `sed -i ''` (GNU sed requires `sed -i` without the empty arg)

Replace the hardcoded path with `$CADDYFILE` (set externally) and make sed cross-platform.

**Patch activate.sh** — replace lines 7 and 14:

```bash
# Old (lines 7, 14):
CADDYFILE="/Users/rotiropi/caddy-rotiropi/Caddyfile"
...
sed -i '' 's/127\.0\.0\.1:8000/127.0.0.1:8001/g' "$CADDYFILE"

# New:
CADDYFILE="${CADDYFILE:-{{STAGING_DIR}}/Caddyfile}"

...
if sed -i '' 's/127\.0\.0\.1:8000/127.0.0.1:8001/g' "$CADDYFILE" 2>/dev/null; then
    :  # macOS sed
else
    sed -i 's/127\.0\.0\.1:8000/127.0.0.1:8001/g' "$CADDYFILE"  # GNU sed
fi
```

**Patch deactivate.sh** — replace line 7:

```bash
# Old (line 7):
CADDYFILE="/Users/rotiropi/caddy-rotiropi/Caddyfile"

# New:
CADDYFILE="${CADDYFILE:-{{STAGING_DIR}}/Caddyfile}"
```

Apply these patches before proceeding. After patching, verify:
```bash
grep -n 'CADDYFILE' {{STAGING_DIR}}/tools/activate.sh {{STAGING_DIR}}/tools/deactivate.sh
# Both should reference ${CADDYFILE} with a fallback, not a hardcoded macOS path.
```

---

## Section 2: Backend Setup

### 2.1 Clone backend at exact commit

```bash
cd {{STAGING_DIR}}
git clone https://github.com/{{REPO_OWNER}}/roti_ropi_pos.git apps/roti_ropi_pos
cd apps/roti_ropi_pos
git fetch origin
git checkout 2b0ee79e5644d4b67b607c9627b4b2ba75260856
git rev-parse HEAD   # must print 2b0ee79e5644d4b67b607c9627b4b2ba75260856
```

If the repo contains a dirty `test_sales.py`, carry it:
```bash
# If git status shows modified: rotri_ropi_pos/tests/test_sales.py:
git diff > ../dirty-test-sales.patch
# After checkout, re-apply:
git apply --reject ../dirty-test-sales.patch
```

### 2.2 Start Frappe/ERPNext via Docker

```bash
cd {{STAGING_DIR}}
docker compose -f pwd.yml up -d db redis-cache redis-queue
# Wait for healthcheck
docker compose -f pwd.yml ps | grep db | grep healthy

docker compose -f pwd.yml up -d configurator
# Wait for configurator to exit (creates common_site_config.json)
docker compose -f pwd.yml logs configurator --tail 20

# Create the site
docker compose -f pwd.yml up create-site
# Wait for completion
docker compose -f pwd.yml logs create-site --tail 20
```

Install the app into the bench:
```bash
docker compose -f pwd.yml exec backend bench get-app roti_ropi_pos --branch main || \
  docker compose -f pwd.yml exec backend bench get-app roti_ropi_pos file:///home/frappe/frappe-bench/apps/roti_ropi_pos

docker compose -f pwd.yml exec backend bench --site frontend install-app roti_ropi_pos
```

### 2.3 Site configuration

```bash
# Set host_name to the public Cloudflare origin
docker compose -f pwd.yml exec backend bench --site frontend set-config host_name "{{PUBLIC_ORIGIN}}"
# e.g. "https://oauth-staging.rotiropi.web.id"

# Set OAuth client ID for Mobile POS
docker compose -f pwd.yml exec backend bench --site frontend set-config mobile_pos_oauth_client_id "rotiropi.mobilepos.task9.staging"
```

Create sites/ symlink so the site responds to the host_name:
```bash
docker compose -f pwd.yml exec backend ln -sfT sites/frontend "sites/$(echo '{{PUBLIC_ORIGIN}}' | sed 's|https://||')"
```

Verify:
```bash
docker compose -f pwd.yml exec backend ls -la sites/
# Should show: {{PUBLIC_HOSTNAME}} → sites/frontend
```

### 2.4 OAuth client configuration

```bash
docker compose -f pwd.yml exec backend bench --site frontend execute frappe.desk.page.setup_wizard.setup_wizard.create_oauth_client --kwargs '{
  "client_id": "rotiropi.mobilepos.task9.staging",
  "client_secret": "{{OAUTH_CLIENT_SECRET}}",
  "redirect_uris": "{{PUBLIC_ORIGIN}}/android/oauth2redirect",
  "grant_type": "Authorization Code",
  "response_type": "Code",
  "scopes": "all",
  "skip_authorization": false,
  "is_public": false
}'
```

Or via bench console:
```bash
docker compose -f pwd.yml exec backend bench --site frontend console
```

Then in the console:
```python
from frappe.integrations.doctype.oauth_client.oauth_client import add_oauth_client
add_oauth_client(
    app_name="Task 9 Mobile POS Staging",
    client_id="rotiropi.mobilepos.task9.staging",
    client_secret="{{OAUTH_CLIENT_SECRET}}",
    redirect_uris="{{PUBLIC_ORIGIN}}/android/oauth2redirect",
    grant_type="Authorization Code",
    response_type="Code",
    scopes="all",
    skip_authorization=False,
)
exit()
```

### 2.5 Start all remaining services

```bash
docker compose -f pwd.yml up -d backend queue-long queue-short scheduler websocket
# Wait for all to be healthy
docker compose -f pwd.yml ps
```

---

## Section 3: Ingress Setup

### 3.1 Option A: Reuse existing named Cloudflare tunnel (if you have the token)

```bash
# The named tunnel is 'rotiropi-pos-staging' pointing to oauth-staging.rotiropi.web.id
cloudflared tunnel login                          # one-time browser auth
cloudflared tunnel list                           # verify 'rotiropi-pos-staging' exists
cloudflared tunnel route dns rotiropi-pos-staging oauth-staging.rotiropi.web.id
```

Then run cloudflared pointing to Caddy on localhost:
```bash
cloudflared tunnel run --url localhost:8080 rotiropi-pos-staging &
```

### 3.2 Option B: Ephemeral quick tunnel (no Cloudflare account needed, but requires host_name/redirect URI changes)

```bash
cloudflared tunnel --url http://localhost:8080 &
# Note the generated trycloudflare.com hostname, e.g. {{TRYCLOUDFLARE_HOSTNAME}}
```

Then update the Frappe site's host_name and OAuth redirect URIs:
```bash
docker compose -f pwd.yml exec backend bench --site frontend set-config host_name "https://{{TRYCLOUDFLARE_HOSTNAME}}"
# Update OAuth Client redirect URIs in the Frappe admin UI → OAuth Client
# Re-create the sites/ symlink with the new hostname
docker compose -f pwd.yml exec backend rm sites/oauth-staging.rotiropi.web.id
docker compose -f pwd.yml exec backend ln -sfT sites/frontend "sites/{{TRYCLOUDFLARE_HOSTNAME}}"
```

### 3.3 Caddy configuration

Edit `{{STAGING_DIR}}/tools/Caddyfile` so it matches the target environment:

```caddyfile
# Global options
{
	auto_https off
	admin off
}

:8080 {
	@assetlinks path /.well-known/assetlinks.json
	handle @assetlinks {
		root * {{STAGING_DIR}}/tools
		rewrite * /assetlinks.json
		header Content-Type application/json
		file_server
	}

	handle {
		reverse_proxy 127.0.0.1:8000 {
			header_up Host frontend
			header_up X-Forwarded-Host {{PUBLIC_HOSTNAME}}
			header_up X-Forwarded-Proto https
		}
	}
}
```

Start Caddy:
```bash
APPLINKS_DIR="{{STAGING_DIR}}/tools" caddy run --config {{STAGING_DIR}}/tools/Caddyfile &
```

### 3.4 Verification steps

```bash
# 1. AssetLinks.json is served correctly
curl -s http://127.0.0.1:8080/.well-known/assetlinks.json | jq '.'
# Should return the JSON array with package_name "com.rotiropi.pos_erpnext"

# 2. AssetLinks is reachable from the public internet
curl -s {{PUBLIC_ORIGIN}}/.well-known/assetlinks.json | jq '.'

# 3. Frappe backend responds through the ingress
curl -s {{PUBLIC_ORIGIN}}/api/method/frappe.ping | jq '.'
# Should return {"message": "pong"}

# 4. The correct site is reached
curl -s -H "Host: frontend" http://127.0.0.1:8000/api/method/frappe.ping | jq '.'
```

---

## Section 4: Test Resource Provisioning

All commands run inside the backend container.

### 4.1 Create the test company (if not already present)

```bash
docker compose -f pwd.yml exec backend bench --site frontend execute frappe.get_doc --kwargs '{"doctype":"Company","company_name":"Task 9 Staging Company","default_currency":"USD","country":"United States"}' --args '{"method":"insert","ignore_permissions":true}'
```

Or via console:
```bash
docker compose -f pwd.yml exec backend bench --site frontend console
```

Then in the console, run each block separately.

### 4.2 Create test user (cashier)

```python
import frappe
user = frappe.get_doc({
    "doctype": "User",
    "email": "task9.cashier@rotiropi.test",
    "first_name": "Task 9",
    "last_name": "Cashier",
    "user_type": "Website User",
    "send_welcome_email": 0,
    "enabled": 1,
})
user.append("roles", {"role": "Mobile POS Cashier"})
user.insert(ignore_permissions=True)
frappe.db.commit()
```

### 4.3 Set cashier password (for OAuth login)

```python
from frappe.utils.password import update_password
update_password("task9.cashier@rotiropi.test", "{{CASHIER_PASSWORD}}")
frappe.db.commit()
```

### 4.4 Verify/create item

```python
# Check if "Consulting" exists
items = frappe.get_all("Item", filters={"item_code": "Consulting"})
if not items:
    item = frappe.get_doc({
        "doctype": "Item",
        "item_code": "Consulting",
        "item_name": "Consulting",
        "item_group": "Services",
        "stock_uom": "Nos",
        "is_stock_item": 0,
        "is_sales_item": 1,
        "standard_rate": 100.00,
    })
    item.insert(ignore_permissions=True)
    frappe.db.commit()
```

### 4.5 Verify Walk In Customer

```python
customer = frappe.get_all("Customer", filters={"customer_name": "Walk In Customer"})
if not customer:
    cust = frappe.get_doc({
        "doctype": "Customer",
        "customer_name": "Walk In Customer",
        "customer_type": "Individual",
        "customer_group": "Individual",
        "territory": "All Territories",
    })
    cust.insert(ignore_permissions=True)
    frappe.db.commit()
```

### 4.6 Create Cash payment mode

```python
mode = frappe.get_all("Mode of Payment", filters={"mode_of_payment": "Cash", "enabled": 1})
if not mode:
    mop = frappe.get_doc({
        "doctype": "Mode of Payment",
        "mode_of_payment": "Cash",
        "enabled": 1,
        "type": "Cash",
    })
    mop.insert(ignore_permissions=True)
    frappe.db.commit()
```

### 4.7 Create POS Profile

```python
profile = frappe.get_all("POS Profile", filters={"name": "Task 9 Mobile POS"})
if not profile:
    pos = frappe.get_doc({
        "doctype": "POS Profile",
        "name": "Task 9 Mobile POS",
        "company": "Task 9 Staging Company",
        "currency": "USD",
        "selling_price_list": "Standard Selling",
        "price_list": "Standard Selling",
        "customer": "Walk In Customer",
        "warehouse": "Stores",
        "disabled": 0,
        "ignore_pricing_rule": 0,
        "allow_user_to_edit_rate": 1,
        "allow_user_to_edit_discount": 1,
        "allow_print_before_pay": 1,
        "update_stock": 0,
        "posa_display_recent_items": 1,
        "apply_discount_on": "Grand Total",
    })
    pos.append("applicable_for_users", {"user": "task9.cashier@rotiropi.test"})
    pos.append("payments", {"mode_of_payment": "Cash"})
    pos.insert(ignore_permissions=True)
    frappe.db.commit()
```

### 4.8 Create POS Opening Entry

```bash
docker compose -f pwd.yml exec backend bench --site frontend execute roti_ropi_pos.api.v1.sessions.open --kwargs '{
  "pos_profile": "Task 9 Mobile POS",
  "opening_balance": {}
}'
```

Or via API after all is running (this is the preferred live approach):
```bash
curl -s -X POST "{{PUBLIC_ORIGIN}}/api/method/roti_ropi_pos.api.v1.sessions.open" \
  -H "Authorization: token {{API_KEY}}:{{API_SECRET}}" \
  -H "Content-Type: application/json" \
  -d '{"pos_profile":"Task 9 Mobile POS","opening_balance":{}}' | jq '.'
```

Confirm the opening was created:
```bash
curl -s "{{PUBLIC_ORIGIN}}/api/resource/POS Opening Entry?filters=[[\"pos_profile\",\"=\",\"Task 9 Mobile POS\"],[\"status\",\"=\",\"Open\"]]" \
  -H "Authorization: token {{API_KEY}}:{{API_SECRET}}" \
  -H "Content-Type: application/json" | jq '.data | length'
# Should return ≥ 1
```

---

## Section 5: Credential Generation

### 5.1 Generate API key for task9.cashier

**Never print the key to terminal output or logs.**

```bash
cd {{STAGING_DIR}}
umask 077
docker compose -f pwd.yml exec -T backend bench --site frontend console <<'PYEOF' > /dev/null
import frappe
from frappe.core.doctype.user.user import generate_keys
key_doc = generate_keys("task9.cashier@rotiropi.test")
with open("/tmp/task9-api-key.json", "w") as f:
    import json
    json.dump({"api_key": key_doc.api_key, "api_secret": key_doc.api_secret}, f)
frappe.db.commit()
exit()
PYEOF

docker compose -f pwd.yml exec backend cat /tmp/task9-api-key.json > credentials/api-key.json
chmod 600 credentials/api-key.json
```

Extract for use in commands (without printing):
```bash
API_KEY=$(jq -r '.api_key' credentials/api-key.json)
API_SECRET=$(jq -r '.api_secret' credentials/api-key.json)
```

### 5.2 Verify auth works

```bash
curl -sf -o /dev/null -w "%{http_code}" \
  "{{PUBLIC_ORIGIN}}/api/method/frappe.auth.get_logged_user" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}"
# Must return 200
```

Expected response body:
```json
{"message": "task9.cashier@rotiropi.test"}
```

---

## Section 6: Proxy Validation

### 6.1 Run synthetic tests

```bash
cd {{STAGING_DIR}}/tools
python3 test_proxy.py
```

Expected output:
```
============================================================
 ALL TESTS PASSED
============================================================
  ✓ PASS: Test 1 — non-matching GET passes through
  ✓ PASS: Test 2 — target sales.submit captured and response dropped (502)
  ✓ PASS: Test 3 — replay passes through normally (201)
  ✓ PASS: All 8 required events present
```

### 6.2 Fix platform issues

| Symptom | Fix |
|---------|-----|
| `ConnectionRefusedError` on upstream check | Verify no process on ports 8002/8099: `lsof -i :8002`, `lsof -i :8099`. Kill stale processes. |
| `PermissionError` | `test_proxy.py` spawns `proxy.py` as a subprocess — ensure `python3` is in PATH. |
| `FileNotFoundError: proxy.py` | `cd` to the `tools/` directory; `test_proxy.py` resolves `proxy.py` relative to its own path. |

---

## Section 7: Fault Test Execution

### 7.1 Generate test UUID

```bash
TEST_UUID=$(uuidgen | tr '[:upper:]' '[:lower:]')
echo "$TEST_UUID" > evidence/test-uuid.txt
```

### 7.2 Build the request body

```bash
cat > evidence/request-body.json <<'REQBODY'
{
  "pos_profile": "Task 9 Mobile POS",
  "items": [
    {
      "item_code": "Consulting",
      "qty": 1,
      "rate": 100.00
    }
  ],
  "customer": "Walk In Customer",
  "payments": [
    {
      "mode_of_payment": "Cash",
      "amount": 100.00
    }
  ]
}
REQBODY
```

### 7.3 Start proxy

```bash
cd {{STAGING_DIR}}/tools
mkdir -p ../evidence
python3 proxy.py \
  --listen-host 127.0.0.1 \
  --listen-port 8001 \
  --upstream-host 127.0.0.1 \
  --upstream-port 8000 \
  --evidence-file {{STAGING_DIR}}/evidence/events.jsonl &
PROXY_PID=$!
sleep 1
```

Verify proxy is listening:
```bash
ss -tlnp | grep 8001   # Linux
# or
lsof -i :8001           # macOS

# Quick health check: pass-through a non-matching request
curl -s http://127.0.0.1:8001/api/method/frappe.ping | jq '.'
```

### 7.4 Activate proxy (patch Caddy to route through :8001)

```bash
cd {{STAGING_DIR}}/tools
chmod +x activate.sh
CADDYFILE="{{STAGING_DIR}}/tools/Caddyfile" ./activate.sh
```

Wait a moment for Caddy reload, then verify routing:
```bash
curl -s {{PUBLIC_ORIGIN}}/api/method/frappe.ping | jq '.'
# Should still return pong (now via proxy :8001 → :8000)
```

### 7.5 Submit original request (expect 502)

```bash
API_KEY=$(jq -r '.api_key' credentials/api-key.json)
API_SECRET=$(jq -r '.api_secret' credentials/api-key.json)

curl -s -w "\n%{http_code}" \
  -X POST "{{PUBLIC_ORIGIN}}/api/method/roti_ropi_pos.api.v1.sales.submit" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: ${TEST_UUID}" \
  -d @evidence/request-body.json \
  -o evidence/original-response.json

# The last line should be: 502
tail -1 evidence/original-response.json
```

### 7.6 Verify fault evidence

```bash
cat evidence/events.jsonl | jq -c '{event, uuid, fault_active, upstream_status}'
```

Required events in order:
```
{"event":"harness_started","fault_active":false,...}
{"event":"original_captured_before_upstream","uuid":"{{TEST_UUID}}",...}
{"event":"fault_armed","uuid":"{{TEST_UUID}}","fault_active":true,...}
{"event":"backend_commit_confirmed_and_fault_disarmed","upstream_status":201,"invoice_ref":"ACC-PSINV-...","fault_active":false,...}
{"event":"original_response_dropped","uuid":"{{TEST_UUID}}","fault_active":false,...}
```

Verify each assertion:
```bash
# fault was armed
jq 'select(.event=="fault_armed") | .fault_active' evidence/events.jsonl | grep true

# backend committed with 201
jq 'select(.event=="backend_commit_confirmed_and_fault_disarmed") | .upstream_status' evidence/events.jsonl | grep 201

# invoice ref present
jq 'select(.event=="backend_commit_confirmed_and_fault_disarmed") | .invoice_ref' evidence/events.jsonl | grep ACC-PSINV-

# response was dropped
jq 'select(.event=="original_response_dropped")' evidence/events.jsonl | grep .
# (non-empty = droplet event exists)

# fault is now disarmed
jq 'select(.event=="backend_commit_confirmed_and_fault_disarmed") | .fault_active' evidence/events.jsonl | grep false

# extract invoice ref for later cleanup
INVOICE_NAME=$(jq -r 'select(.event=="backend_commit_confirmed_and_fault_disarmed") | .invoice_ref' evidence/events.jsonl)
echo "$INVOICE_NAME" > evidence/test-invoice.txt
```

### 7.7 Replay (expect 200)

```bash
curl -s -w "\n%{http_code}" \
  -X POST "{{PUBLIC_ORIGIN}}/api/method/roti_ropi_pos.api.v1.sales.submit" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}" \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: ${TEST_UUID}" \
  -d @evidence/request-body.json \
  -o evidence/replay-response.json

# The last line should be: 200
tail -1 evidence/replay-response.json
```

### 7.8 Verify replay evidence

```bash
cat evidence/events.jsonl | jq -c 'select(.event=="replay_observed" or .event=="replay_response_relayed" or .event=="complete")'
```

Required replay events:
```json
{"event":"replay_observed","uuid":"{{TEST_UUID}}","uuid_match":true,"body_hash_match":true,...}
{"event":"replay_response_relayed","upstream_status":200,"fault_active":false,...}
{"event":"complete",...}
```

Verify:
```bash
jq 'select(.event=="replay_observed") | {uuid_match, body_hash_match}' evidence/events.jsonl
# Both must be true

jq 'select(.event=="replay_response_relayed") | .upstream_status' evidence/events.jsonl | grep 200

jq 'select(.event=="complete")' evidence/events.jsonl | grep .
```

### 7.9 Prove exactly-once

```bash
# Exactly one POS Invoice with the test UUID
INVOICE_COUNT=$(curl -s "{{PUBLIC_ORIGIN}}/api/resource/POS Invoice?filters=[[\"idempotency_key\",\"=\",\"${TEST_UUID}\"]]" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}" \
  -H "Content-Type: application/json" | jq '.data | length')
echo "Invoice count for test UUID: $INVOICE_COUNT"
# Must be: 1

# Or by invoice name
INVOICE_NAME=$(cat evidence/test-invoice.txt)
curl -s "{{PUBLIC_ORIGIN}}/api/resource/POS Invoice/${INVOICE_NAME}" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}" \
  -H "Content-Type: application/json" | jq '{name: .data.name, status: .data.status, docstatus: .data.docstatus}'
# status should be "Paid", docstatus should be 1 (submitted)

# Verify no duplicate with same UUID
curl -s "{{PUBLIC_ORIGIN}}/api/resource/POS Invoice?filters=[[\"idempotency_key\",\"=\",\"${TEST_UUID}\"],[\"docstatus\",\"=\",1]]" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}" | jq '.data | length'
# Must be exactly 1
```

---

## Section 8: Cleanup

### 8.1 Cancel the test invoice

```bash
INVOICE_NAME=$(cat evidence/test-invoice.txt)

curl -s -X PUT "{{PUBLIC_ORIGIN}}/api/resource/POS Invoice/${INVOICE_NAME}" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}" \
  -H "Content-Type: application/json" \
  -d '{"docstatus": 2}' | jq '.data.docstatus'
# Should return 2 (cancelled)
```

### 8.2 Close the POS Opening Entry

First find the opening entry:
```bash
OPENING_NAME=$(curl -s "{{PUBLIC_ORIGIN}}/api/resource/POS Opening Entry?filters=[[\"pos_profile\",\"=\",\"Task 9 Mobile POS\"],[\"status\",\"=\",\"Open\"],[\"user\",\"=\",\"task9.cashier@rotiropi.test\"]]" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}" | jq -r '.data[0].name')
echo "Opening Entry: $OPENING_NAME"
```

Cancel the opening entry:
```bash
curl -s -X PUT "{{PUBLIC_ORIGIN}}/api/resource/POS Opening Entry/${OPENING_NAME}" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}" \
  -H "Content-Type: application/json" \
  -d '{"status": "Closed"}' | jq '.data.status'
# Should return "Closed"
```

### 8.3 Delete test records (optional but recommended)

```bash
# Delete cancelled invoice (requires Delete permission)
curl -s -X DELETE "{{PUBLIC_ORIGIN}}/api/resource/POS Invoice/${INVOICE_NAME}" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}"

# Delete cancelled opening entry
curl -s -X DELETE "{{PUBLIC_ORIGIN}}/api/resource/POS Opening Entry/${OPENING_NAME}" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}"

# Delete the API key to prevent credential leakage
docker compose -f pwd.yml exec backend bench --site frontend console <<'PYEOF'
import frappe
frappe.db.delete("User Api Key", {"user": "task9.cashier@rotiropi.test"})
frappe.db.commit()
exit()
PYEOF
```

### 8.4 Deactivate proxy

```bash
cd {{STAGING_DIR}}/tools
chmod +x deactivate.sh
CADDYFILE="{{STAGING_DIR}}/tools/Caddyfile" ./deactivate.sh

# Kill proxy process
kill $PROXY_PID 2>/dev/null || true
wait $PROXY_PID 2>/dev/null || true
```

### 8.5 Verify clean state

```bash
# Proxy is dead
ss -tlnp | grep 8001 && echo "FAIL: proxy still listening" || echo "PASS: proxy port free"
# or: lsof -i :8001 (macOS)

# Ingress routes directly to Frappe (port 8000, not 8001)
grep "127.0.0.1:8000" {{STAGING_DIR}}/tools/Caddyfile && echo "PASS: Caddyfile targets :8000" || echo "FAIL"

# Backend still responds
curl -sf {{PUBLIC_ORIGIN}}/api/method/frappe.ping | jq '.' && echo "PASS: backend reachable"

# No lingering invoices for the test UUID
curl -s "{{PUBLIC_ORIGIN}}/api/resource/POS Invoice?filters=[[\"idempotency_key\",\"=\",\"${TEST_UUID}\"]]" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}" | jq '.data | length'
# Should be 0

# No active opening for the test cashier
curl -s "{{PUBLIC_ORIGIN}}/api/resource/POS Opening Entry?filters=[[\"pos_profile\",\"=\",\"Task 9 Mobile POS\"],[\"status\",\"=\",\"Open\"],[\"user\",\"=\",\"task9.cashier@rotiropi.test\"]]" \
  -H "Authorization: token ${API_KEY}:${API_SECRET}" | jq '.data | length'
# Should be 0

# Credential file can be shredded
shred -u credentials/api-key.json 2>/dev/null || rm -P credentials/api-key.json 2>/dev/null || rm credentials/api-key.json
```

---

## Section 9: Evidence

### 9.1 Format

Two files constitute the evidence package, aligned with the Task 6 format:

**File 1:** `evidence/events.jsonl` — machine-parseable proxy event log (JSONL, one JSON object per line). This is the authoritative record of the proxy state machine transitions.

**File 2:** `evidence/summary-{{DATE}}.md` — human-readable markdown summary modeled on `task6-api25-pass-20260803.md`. Use this template:

```markdown
# Task 9 response-drop staging evidence

- Protocol: `mobile-pos-response-drop/v1`
- Operator evidence ID: `task9-response-drop-{{DATE}}`
- Timestamp: `{{ISO_TIMESTAMP}}`
- Operator: Mobile POS staging operator
- Device: staging-only (curl + API key, no Android APK)
- Backend commit: `2b0ee79e5644d4b67b607c9627b4b2ba75260856`
- Staging site: `task9-staging.localhost`
- POS Profile: `Task 9 Mobile POS`
- Original request UUID: `{{TEST_UUID}}`
- Replay request UUID: `{{TEST_UUID}}` (same)

## Original attempt and fault removal

Sanitized final proxy facts:

​```text
event=original_captured_before_upstream uuid={{TEST_UUID}}
event=fault_armed fault_active=true
event=backend_commit_confirmed_and_fault_disarmed upstream_status=201 invoice_ref=ACC-PSINV-{{INVOICE_NUMBER}}
event=original_response_dropped fault_active=false
​```

## Replay and reconciliation

​```text
event=replay_observed uuid_match=true body_hash_match=true
event=replay_response_relayed upstream_status=200
event=complete
​```

## Authoritative exactly-one result

Before cleanup, the targeted backend queries returned:

​```text
invoice_count_for_test_uuid=1
invoice_status=Paid
invoice_docstatus=1 (submitted)
duplicate_count=0
​```

## Targeted cleanup

​```text
invoice_cancelled=true
opening_closed=true
proxy_listener_count=0
api_key_deleted=true
​```

## Final conclusion

- `PASS` — post-commit response loss on `sales.submit` captured the original request, confirmed backend commit at HTTP 201, dropped the response (502), replayed the exact UUID/body through the now-disarmed proxy (200), produced exactly one backend logical result, and cleaned up completely.
```

### 9.2 Evidence location

Save everything under:
```
{{STAGING_DIR}}/evidence/
├── events.jsonl              # proxy event log (JSONL)
├── summary-{{DATE}}.md        # human-readable summary
├── request-body.json          # the test request body
├── test-uuid.txt              # the test UUID
├── test-invoice.txt           # the resulting invoice name
├── original-response.json     # original response body + status code
└── replay-response.json       # replay response body + status code
```

### 9.3 Evidence integrity

After the test, compute a manifest:
```bash
cd {{STAGING_DIR}}/evidence
sha256sum events.jsonl summary-*.md request-body.json test-uuid.txt > evidence-manifest.sha256
```

---

## Appendix A: Quick-reference environment variables

```bash
export STAGING_DIR="{{STAGING_DIR}}"
export PUBLIC_ORIGIN="{{PUBLIC_ORIGIN}}"             # e.g. https://oauth-staging.rotiropi.web.id
export PUBLIC_HOSTNAME="{{PUBLIC_HOSTNAME}}"          # e.g. oauth-staging.rotiropi.web.id
export API_KEY=$(jq -r '.api_key' "$STAGING_DIR/credentials/api-key.json")
export API_SECRET=$(jq -r '.api_secret' "$STAGING_DIR/credentials/api-key.json")
export TEST_UUID=$(cat "$STAGING_DIR/evidence/test-uuid.txt")
export CADDYFILE="$STAGING_DIR/tools/Caddyfile"
```

## Appendix B: Troubleshooting

| Issue | Diagnosis | Fix |
|-------|-----------|-----|
| Docker compose services won't start | `docker compose -f pwd.yml ps` | Check port 8080, 3306, 6379 not in use. `docker compose -f pwd.yml down -v && docker compose -f pwd.yml up -d` |
| Proxy events missing `backend_commit_confirmed` | Upstream didn't return 201 or invoice ref parsing failed | Check Frappe bench logs. Verify `sales.submit` returns `{"message":{"sale":{"summary":{"name":"ACC-PSINV-..."}}}}`. |
| Replay returns 409 Conflict | Invoice already submitted (duplicate detection) | The proxy prevents this, but if it happens the invoice exists. Check that `exactly-once` still holds. |
| `caddy reload` fails with `admin off` | Caddyfile has `admin off` global option | Replace `caddy reload --config "$CADDYFILE"` with `caddy reload --config "$CADDYFILE" --address localhost:2019` after adding `admin :2019` to the Caddyfile. Or simply kill and restart Caddy. |
| `sed -i ''` fails on Linux | macOS syntax | Use the cross-platform patched version from Section 1.3. |
| Cloudflared tunnel fails to route | DNS not configured, tunnel not running | For named tunnel: verify `cloudflared tunnel route dns`. For quick tunnel: note the trycloudflare.com hostname and update host_name everywhere. |
