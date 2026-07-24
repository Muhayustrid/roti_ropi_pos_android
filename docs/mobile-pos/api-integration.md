# Android Mobile POS API Integration

## Authority

The complete endpoint inventory, payloads, stable envelopes, error fields, and
compatibility rules live in backend `api-contract.md`. Android must not copy or
extend those contracts independently.

## Allowed Surface

| Endpoint | Method | Android use |
| --- | --- | --- |
| `bootstrap.get` | GET | User, profiles, opening, capabilities |
| `sessions.current` | GET | Reconcile current opening |
| `sessions.open` | POST | Open shift |
| `customers.search` | GET | Paginated registered-customer search |
| `catalog.search` | GET | Paginated catalog search |
| `catalog.scan` | POST | Barcode, batch, serial, and UOM resolution |
| `catalog.quote_item` | POST | Non-authoritative item quote |
| `sales.submit` | POST | Fully settled POS Invoice |
| `sales.get` | GET | POS Invoice detail |
| `sales.list` | GET | Paginated POS Invoice history |
| `sales.create_return` | POST | POS Invoice return |
| `closing.preview` | GET | Server-derived closing preview |
| `closing.submit` | POST | Closing mutation |
| `closing.status` | GET | Closing state polling |

No generic `/api/resource`, arbitrary `/api/method`, Desk, upload, Sales
Invoice, cancellation, health, or return-preview call is allowed.

OAuth is the only control-plane exception. Android launches only
`<canonical-origin>/api/method/frappe.integrations.oauth2.authorize` and sends
token exchange/refresh only to
`<canonical-origin>/api/method/frappe.integrations.oauth2.get_token`. This does
not permit discovery, dynamic method paths, or another Frappe method call.

## Transport

- Use only the canonical configured backend origin from `authentication.md`.
- Construct requests from the checked-in endpoint table; never accept an
  arbitrary absolute URL.
- Send JSON with `Content-Type: application/json`.
- Attach Bearer only after exact canonical-origin matching.
- Disable redirect following for authenticated API traffic.
- Send `X-Idempotency-Key` on every mutation.
- Do not send identity, company, account, server totals, document state, or
  document names unless the v1 contract explicitly defines the field.
- Preserve `Retry-After` when supplied.
- Redact authorization, cookies, OAuth fields, and mutation bodies from logs.

Eligible reads may perform one serialized token refresh and one retry after
HTTP 401. The four mutations never receive an automatic transport retry.
Mutation 401 enters durable `auth_required` recovery.

## Known-Offline Mutation Start

Connectivity state is an optimization, not proof that ERPNext is reachable.
`ConnectivityStatus` classifies a current platform snapshot as `Online`,
`KnownOffline`, or `Unknown`. Only a current, unambiguous absence of an usable
network is `KnownOffline`; ambiguous capabilities, stale callbacks, local/LAN
reachability, captive state, or inspection failure are `Unknown`.

When connectivity is known unavailable before a new mutation starts:

- Preserve all current user input.
- Display that the transaction was not submitted.
- Generate no UUID.
- Serialize and persist no mutation.
- Enqueue no worker.
- Invoke no transport.
- Provide an explicit Retry action.

Connectivity restoration may enable Retry but never starts that never-prepared
action automatically. It may satisfy the network constraint for a mutation
already durably persisted before connectivity was lost. If connectivity is
unknown or appears available, use the normal prepare-before-send flow and
classify the real transport result.

## Stable Envelope Parsing

Endpoint responses have Frappe's outer `message` property and the Mobile POS
inner envelope.

Android parsing must:

- Require the outer `message`.
- Distinguish `ok: true` data from `ok: false` expected errors.
- Preserve `meta.api_version`, `request_id`, `server_time`, and `replayed`.
- Reject an incompatible API major version.
- Ignore additive unknown fields.
- Treat missing required fields or wrong types as protocol errors.
- Decode decimal and quantity values as strings, never `Double`.

Pre-dispatch failures may use native Frappe bodies. Android classifies by HTTP
status without requiring the stable envelope:

| HTTP | Android classification |
| --- | --- |
| 401 | Authentication required |
| 403 | Route forbidden or method forbidden |
| 404 | Route not found |
| 429 | Rate limited |
| 500/503 | Server unavailable |
| Other malformed response | Protocol error |

Raw traceback, exception class, HTML, or internal server text is never shown as
cashier guidance.

## DTO Boundary

- DTO classes contain only documented fields.
- DTOs map to separate domain and UI models in `MobilePosRepository`.
- Decimal strings map to `BigDecimal` only at the domain boundary.
- Unknown enum values map to an explicit unsupported state, not a crash.
- UI does not depend on Frappe field names outside v1 DTOs.
- API fixtures cover additive fields and every stable error used by a flow.

Task 2 DTOs and fixtures derived from approved contract examples prove parser
and serializer compatibility only; they are not runtime integration evidence
and are not final for a blocked flow. Each feature task may update its DTO and
repository signature only after its backend phase and contract decisions are
approved.

## Bootstrap and Capabilities

Bootstrap is the application routing authority.

- No selected profile means all mutation capabilities are false.
- One eligible profile may be selected automatically.
- Multiple eligible profiles require cashier selection.
- `open_session`, `submit_sale`, `create_return`, and `close_session` control
  action availability.
- `cancel_sale` remains false.
- Perform one coalesced authoritative bootstrap refresh after:
  1. successful authentication or authentication recovery;
  2. explicit profile selection or profile change;
  3. successful or replayed opening completion;
  4. successful or replayed sale completion;
  5. successful or replayed return completion;
  6. accepted closing submission; and
  7. terminal closing status recovery.
- Bootstrap completion, capability observation, UI rendering, and refresh
  failure never recursively trigger refresh.
- A required failed refresh leaves mutations disabled until explicit Retry.
- Capabilities improve UX but never replace server authorization.

## Read Flows

### Customer

- Debounce search by 300 ms.
- Request page size 20 and cap it at 100.
- Cancel obsolete queries.
- Keep only the active result page and visible selection.
- Never create or modify a Customer.
- Use `is_default_walk_in` to control optional walk-in display-name UI.

### Catalog

- Treat price and stock as snapshots.
- Do not poll.
- Quote after cart-affecting changes.
- Cancel obsolete quote requests.
- Display quote warnings without converting them into local business rules.

### Barcode, UOM, Batch, and Serial

The MVP accepts scanner-wedge text or manual scan-value entry.

- Send the exact value to `catalog.scan`.
- Use the returned item, UOM, conversion factor, warehouse, batch, or serial.
- Quote the resolved item before adding or updating the cart.
- Do not derive UOM conversion locally.
- Do not browse or cache a local batch or serial inventory.
- An item requiring an identifier cannot be submitted until the approved
  server flow has supplied it.
- Camera capture requires separate CameraX approval.

The current quote request has no serial-selection field. Task 8 stops until
backend/product owners decide whether a serial change requires an identical
refresh using the existing fields or a contracted serial-aware quote input.

### History

- Use `sales.list` with deterministic, bounded pagination.
- Use `sales.get` for detail.
- Preserve server-provided walk-in display names.
- Do not infer another cashier's or profile's visibility.

## Mutation Flows

### Opening

Before `sessions.open`, Android must receive the approved opening payment-mode
projection. That projection is absent from the current v1 DTO documentation,
so opening implementation is blocked until the backend contract supplies it.

Amount-entry work is also blocked until the contract defines accepted locale
syntax, precision, scale, bounds, and no-rounding behavior. Android may validate
syntax but never round or derive an authoritative amount.

After the contract is complete:

1. Build balances only from server-provided modes.
2. Persist request body and UUID.
3. Submit once through the recovery coordinator.
4. Reconcile timeout by replay or `sessions.current`.
5. Route to the sale workspace only after terminal server state.

### Cart and Quote

- The cart stores item identity, cashier quantity, resolved UOM, batch, serials,
  and the latest item quote.
- Display values are labeled as estimates.
- Android does not calculate authoritative tax or grand total.
- A stale quote never authorizes submission.
- The cart is capped at 50 distinct rows.

### Payment, Sale, Invoice, and Receipt

The current contract does not provide an authoritative cart payable value before
`sales.submit`, while the request requires both `client_accepted_grand_total`
and fully settled payment rows. Item-level quote data is insufficient for a
server-authoritative tax and cart total.

Sale and payment implementation is therefore blocked until the backend contract
defines a complete authoritative payable workflow and exposes permitted payment
modes.

The backend says overpayment and change remain subject to ERPNext core rules,
but it does not approve an Android request-construction or cashier UX flow.
Android therefore supports exact settlement only after the payable gate is
resolved. It must not accept overpayment or calculate change locally.

Server-provided `change_amount` remains parseable read/receipt data and does not
authorize an overpayment flow. Supporting overpayment requires a separate
contract defining eligible modes, tendered and allocated amounts, authoritative
change, errors, receipt behavior, and fixtures.

After that gate:

1. Display the authoritative payable value.
2. Accept only server-provided payment modes.
3. Require payment rows to sum exactly to the authoritative payable value.
4. Persist the exact request and a new UUID.
5. Submit through `sales.submit`.
6. Handle `PRICE_CHANGED` as a terminal rejected action requiring explicit
   cashier review and a new logical request.
7. Render the receipt only from successful `SaleDetail`.
8. Show request ID and business reference in support details.

### Return

The current contract requires negative payment rows but does not provide an
authoritative refund quote or remaining-returnable projection. Return submission
is blocked until the backend documents a complete refund construction workflow
that does not require Android accounting calculations.

After that gate:

1. Load the source POS Invoice.
2. Let the cashier select rows and quantities.
3. Require a trimmed, non-empty reason.
4. Display server-authoritative return limits and refund values.
5. Persist and submit one idempotent return.
6. Handle `RETURN_LIMIT_EXCEEDED` without assuming success.
7. Render the return receipt from the terminal server response.

### Closing

1. Call `closing.preview`.
2. Display expected values as server-derived and collect counted balances.
3. Persist and call `closing.submit`.
4. Treat an unknown transport result as local `waiting_retry` and replay only
   the persisted submit after contracted backoff.
5. Treat `REQUEST_IN_PROGRESS` as confirmed request Processing and replay only
   the persisted submit after contracted backoff.
6. Treat a successful first or replayed submit as Completed and persist its
   stored result.
7. Treat the stored stable expected error as Rejected and require a new,
   explicitly initiated corrected action where permitted.
8. Poll `closing.status` only after Completed submit returned `queued`.
9. Treat `submitted`, `failed`, and manager-controlled `cancelled` as terminal.
10. Treat a bare `draft` document status without persisted submit disposition as
   protocol ambiguity; never infer replay safety from Draft.
11. Never create a replacement closing.

## Retry Boundary

Read retries are bounded to one refresh and one retry. Mutation resolution
follows `state-and-recovery.md`, always uses the persisted UUID/body, and is
owned by `RecoveryCoordinator`, not the transport.

The API client never retries a mutation invisibly before durable persistence.

## Compatibility Gate

- Additive fields are ignored.
- Required field meaning or type changes stop Android release.
- New error codes are displayed safely as unsupported server responses until
  mapped.
- Breaking behavior requires a versioned backend contract.
- Fixtures must come from the approved backend implementation or reviewed
  contract examples.

Each fixture records its endpoint, owning backend phase, source type, backend
version/SHA, review reference, and confirmation that it contains no credential
or production personal data. Contract-example fixtures are labeled as such until
the endpoint implementation is verified. DTO and fixture changes are reviewed
together.

## Integration Stop Conditions

- A required endpoint or DTO field is not implemented and verified.
- Payment modes are unavailable.
- Android would need to calculate authoritative sale or refund values.
- A response cannot be classified as terminal or recoverable.
- A backend phase gate or contract fixture fails.
- A blocked flow would require final DTO fields or repository signatures before
  its backend contract decision.
- Serial requote or decimal input semantics are unresolved for the active task.
- An implementation proposes a generic Frappe or unapproved endpoint.
