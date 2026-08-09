# POSERPNext Android Rules

Read this file before changing the Android repository. These rules apply to the entire project.

## Communication and Change Control

- Communicate with the user in Indonesian.
- Write repository Markdown, code comments, technical documentation, test names, and commit messages in English.
- Do not commit, push, publish, deploy, or begin a later implementation phase without explicit user approval.
- Keep backend and Android work in their own repositories and review their diffs independently.

## Approved Android Stack

- Use Kotlin for application code.
- Jetpack Compose is approved for Mobile POS feature UI and the pre-Task-12
  Stitch design refresh. No per-screen Compose approval is required within
  that scope.
- Reuse the existing Compose, Material 3, theme, navigation, and testing stack.
  Do not add another UI framework.
- Keep existing XML/ViewBinding platform, authentication, and legacy host
  surfaces unless their migration is separately approved. Do not perform a
  broad XML-to-Compose migration as part of a feature redesign.
- Keep `minSdk 23` support.
- Prefer Android platform and AndroidX APIs. Add only lightweight, maintained dependencies with a clear measurable need.
- Design for low-end devices: bounded lists, paginated data, limited allocations, no unnecessary polling, efficient image loading, and no large in-memory ERPNext document graphs.

## Authentication and Secret Handling

- Use OAuth 2.0 Authorization Code with mandatory PKCE S256.
- Treat the app as a public OAuth client. Never embed a client secret.
- Launch authorization in the system browser or a secure Custom Tab; do not capture ERPNext credentials in a WebView.
- Generate a high-entropy verifier for each authorization attempt, send its S256 challenge, validate state and redirect URI, and treat authorization codes as single-use.
- Store access and refresh tokens with Android Keystore-backed encryption and exclude them from backups, logs, analytics, screenshots, and crash reports.
- Never use API keys, Basic credentials, shared cashier users, Administrator credentials, or service-account credentials.
- Each cashier authenticates as an individual ERPNext user. Logout removes local tokens and sensitive cached responses; server revocation remains a manager ERPNext Desk operation until a no-secret public-client revocation flow is verified.
- Use HTTPS only and reject cleartext endpoints.

## Server Authority and API Boundary

- Call only the versioned Mobile POS API documented under `apps/roti_ropi_pos/docs/mobile-pos/`.
- Do not call generic Frappe resource APIs, arbitrary whitelisted methods, ERPNext document save APIs, or core POS helpers directly.
- ERPNext owns Customer, POS Profile, price, discount, tax, stock, warehouse, UOM conversion, batch, serial, payment account, totals, status, and accounting decisions.
- Treat catalog prices and stock as display snapshots. Accept authoritative server changes and stable validation errors during submit.
- Never calculate or persist authoritative accounting totals locally.
- Do not create a permanent Customer for a walk-in buyer. Use registered-customer selection or the POS Profile default walk-in Customer with an optional display name.
- The MVP supports POS Invoice only and does not support partially paid invoices.
- Multiple payment modes are valid only when the server confirms the invoice is fully settled.

## Transaction IDs and Recovery

- Generate one lowercase UUID idempotency key before each logical mutation and send it as `X-Idempotency-Key`.
- Persist the key, normalized request body, endpoint, creation time, and local recovery state before sending.
- Reuse the same key and body after timeout, connection loss, process death, or app restart. Never generate a new key merely because the result is unknown.
- Treat `REQUEST_IN_PROGRESS` and queued closing as recoverable states and poll only the documented status endpoint with bounded backoff.
- On HTTP 401, preserve pending transactions, stop mutation retries, reauthenticate, then resume with the original key.
- Retire local transaction data only after a terminal server response is safely persisted.
- Never implement an offline ledger or assume a local pending transaction was accepted by ERPNext.

## Architecture and Performance

- Keep network DTOs separate from domain/UI models and ignore additive unknown response fields.
- Use a small repository/data-source boundary around the Mobile POS API.
- Use structured concurrency and lifecycle-aware cancellation. Do not leak Activities, Views, or authentication callbacks.
- Use ViewBinding only between view lifecycle creation and destruction.
- Paginate customer, catalog, and sale-history results. Debounce search and cancel obsolete requests.
- Use WorkManager only for recoverable, durable background retries with network constraints; do not use it for immediate UI calls.
- Avoid dependency-heavy DI, ORM, navigation, serialization, or image frameworks unless a measured requirement justifies them.

## Testing and Verification

- Use test-driven development for transaction, authentication, parsing, and recovery behavior.
- Run `./gradlew testDebugUnitTest`, `./gradlew lintDebug`, and `./gradlew assembleDebug` for normal verification.
- Run `./gradlew connectedDebugAndroidTest` when an emulator/device is available.
- Test API 23 and a current target API, XML/ViewBinding lifecycle, OAuth PKCE failures, token redaction/storage, DTO compatibility, full payment, customer selection, idempotent replay, process death, queued closing, and low-memory behavior.
- Verify the final APK/config contains no client secret, API key, shared credential, token, verifier, or administrator data.
- Do not claim completion without fresh command output and an inspected intended diff.

## Skills and Navigation

- Use `brainstorming` before new user-visible behavior, `writing-plans` for approved multi-step work, `test-driven-development` during implementation, `systematic-debugging` for failures, `requesting-code-review` before completion, and `verification-before-completion` before success claims.
- When `.codegraph/` exists, use CodeGraph before broad repository searches or direct code reads. Query affected symbols and callers before editing; fall back to targeted reads or broad search only when CodeGraph cannot answer.
- Backend plan and contracts: `/Users/rotiropi/DockerERPNext/frappe_docker/development/frappe-bench/apps/roti_ropi_pos/docs/mobile-pos/`.

## Android Skill Selection

- Android skills root: `/Users/rotiropi/DockerERPNext/ai-skills/android/skills/`.
- Select skills by task and read the relevant `SKILL.md` before implementation. Do not assume one skill applies to the entire project or copy skill contents into this file.
- For Android CLI, project inspection, device interaction, and journeys, use `devtools/android-cli/SKILL.md` and its task-relevant references.
- For test setup and infrastructure, use `testing/testing-setup/SKILL.md` and follow its Views/Espresso path.
- For OAuth redirects, incoming deep links, exported components, and Intent handling, use `security/android-intent-security/SKILL.md`.
- No general OAuth 2.0/OIDC/PKCE Android skill is currently present. `identity/verified-email/SKILL.md` concerns Credential Manager verified-email/OpenID4VP and must not replace the approved ERPNext OAuth PKCE flow.
- For an approved R8/release-hardening task, use `performance/r8-analyzer/SKILL.md`. The current release build has optimization disabled, so do not describe it as optimized.
- For low-end profiling when a Perfetto trace exists, use `profilers/perfetto-trace-analysis/SKILL.md` and `profilers/perfetto-sql/SKILL.md`, then verify on representative constrained hardware and API 23.
- Read `camera/camerax/SKILL.md` only when camera-based scanning is explicitly approved; retain the current XML/`PreviewView` camera surface unless its migration is separately approved.
- For approved Mobile POS Compose UI work, use
  `jetpack-compose/theming/styles/SKILL.md` for design-system translation and
  `jetpack-compose/adaptive/SKILL.md` for compact/expanded layouts when relevant.
- `jetpack-compose/migration/migrate-xml-views-to-jetpack-compose/SKILL.md`
  requires separate explicit approval and must not be used for unrelated
  screens or broad migration.
- Android skills are guidance only. These project rules, actual Gradle configuration, Android source, official Android behavior, and executable tests remain authoritative.
