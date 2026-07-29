# Android Mobile POS Authentication

## Authority

Server OAuth and route rules are defined by backend
[`authentication.md`](backend/authentication.md) and
[`api-contract.md`](backend/api-contract.md). This document defines only the
Android public-client lifecycle.

## Required Protocol

- OAuth 2.0 Authorization Code.
- Mandatory PKCE S256.
- Public client with no client secret.
- Individual cashier identity.
- System browser or secure Custom Tab.
- Bearer access token for v1 API calls.
- Refresh token when issued by the approved OAuth client.
- HTTPS only.

API keys, Basic credentials, shared users, passwords captured by the app,
Administrator credentials, embedded WebViews, and service accounts are
prohibited.

## Per-Environment App Link Gates

Authorization is enabled independently for debug, staging, and production.
Each environment requires:

- One canonical backend origin.
- One public OAuth client ID.
- The fixed authorization endpoint at
  `/api/method/frappe.integrations.oauth2.authorize` on that origin.
- The fixed token endpoint at
  `/api/method/frappe.integrations.oauth2.get_token` on that origin.
- Exact requested scope `all`.
- One exact HTTPS redirect URI.
- The installed application's exact application ID.
- The SHA-256 fingerprint of the certificate signing that installed APK.
- A matching OAuth redirect allowlist entry.
- A matching `assetlinks.json` package and certificate association.
- Verification of the merged manifest's exact host and path restriction.

| Environment | Gate |
| --- | --- |
| Debug/local | Approved non-production origin, client, redirect, application ID, and debug signing fingerprint. No HTTP exception is currently approved. |
| Staging | Approved staging origin, client, redirect, application ID, distribution certificate, and deployed staging association. |
| Production | Final production origin, client, redirect, application ID, and actual app-signing certificate. With Play App Signing, use the app-signing certificate, not the upload certificate. |

Debug or staging approval does not approve production. Production remains
blocked until signing ownership and fingerprints are explicitly approved.
Local `adb` App Link overrides are test aids, not deployment evidence.

A custom URI scheme requires separate explicit approval.

## Authorization Flow

1. Load the approved canonical origin, OAuth client ID, redirect URI, fixed
   authorization/token paths, and scope `all`.
2. Generate a cryptographically random authorization state and PKCE verifier
   for this attempt.
3. Derive the S256 code challenge.
4. Atomically persist the encrypted active attempt before browser launch.
5. Open only
   `<canonical-origin>/api/method/frappe.integrations.oauth2.authorize` in the
   system browser or Custom Tab. Browser navigation may reach backend login and
   approve pages; Android never selects those URLs.
6. Receive the redirect through AppAuth's single exported
   `RedirectUriReceiverActivity`, restricted to the exact approved URI.
7. Deliver the result through an explicit PendingIntent to the non-exported
   `AuthCompletionActivity`.
8. Validate action, exact URI, state, OAuth error fields, and single-use attempt
   in `OAuthCoordinator`.
9. Exchange the code at the fixed token path on the canonical origin without
   Basic authentication, a client secret, or redirect following.
10. Atomically persist tokens with canonical origin and client binding.
11. Delete the consumed attempt after terminal success or matching terminal
    OAuth failure.
12. Call bootstrap and derive application navigation from its result.

Cold and warm completion delivery both enter `AuthCompletionActivity` and call
the same `OAuthCoordinator` validation path.

## OAuth Attempt Storage

`OAuthAttemptStore` is separate from `TokenStore` and contains only:

- Format version.
- Canonical backend origin.
- OAuth client ID.
- OAuth state.
- PKCE verifier.
- Exact redirect URI and callback metadata.
- Creation and expiry timestamps.
- Durable phase: `pending` or `consumed`.

The proposed Phase 0 lifetime is 10 minutes and remains a Task 3 approval gate.
The record uses application-private Keystore-backed AES-GCM encryption and
`AtomicFile` replacement. The complete record is committed before browser
launch.

The callback flow:

1. Loads the encrypted attempt after warm delivery or process restart.
2. Rejects a missing, expired, malformed, tampered, origin-mismatched,
   client-mismatched, redirect-mismatched, or already-consumed attempt.
3. Atomically marks the matching attempt consumed before code exchange.
4. Exchanges the code once.
5. Deletes the attempt after terminal success, cancellation, matching OAuth
   error, or expiry.

After restart, a `consumed` attempt never exchanges its code again. If valid
origin/client-bound tokens were already persisted, startup deletes the stale
attempt and continues to bootstrap. Otherwise it deletes the attempt and
requires a new browser authorization. This safely handles process death before,
during, or after an exchange whose outcome is locally unknown.

A mismatched unsolicited callback is rejected without exchanging a code and
without extending the attempt lifetime. It does not disclose the stored state
or verifier. The original pending attempt and its original expiry remain intact
and usable by the matching callback.

Process-death verification uses two instrumentation invocations:

1. Start authorization and persist the attempt.
2. Exit the first runner and force-stop the application.
3. Relaunch and deliver the matching callback.
4. Restore and validate the attempt through the normal coordinator path.
5. Use a test-only injected exchanger to return a successful token response.
6. Verify token persistence and attempt deletion.

Additional injected process-death boundaries verify:

- Death after `consumed` persistence but before token persistence causes no
  exchange after restart and requires a new authorization.
- Death after token persistence but before attempt deletion retains the bound
  tokens, deletes the consumed attempt, and performs no second exchange.

The real browser/App Link journey is verified separately in staging.

## Redirect Component Security

- Export only AppAuth's `RedirectUriReceiverActivity` for the approved redirect.
- Restrict that receiver's BROWSABLE intent filter to the exact approved URI and
  use `android:autoVerify="true"`.
- Keep `AuthCompletionActivity` non-exported and target its completion and
  cancellation PendingIntents explicitly with the AppAuth/API-required
  mutability flags.
- Do not add a redirect intent filter to `MainActivity` or another component.
- Reject missing, duplicate, malformed, or unexpected parameters.
- Reject state mismatch before exchanging a code.
- Never forward an incoming nested Intent.
- Never accept navigation destinations or arbitrary URLs from redirect extras.
- Apply the same coordinator validation to cold and warm completion delivery.
- Treat forged explicit Intents to the exported receiver as untrusted; App Link
  verification does not replace state, exact-URI, and PKCE checks.
- Clear the consumed attempt before accepting another code.
- Show a generic user error while retaining a non-sensitive diagnostic reason.
- Never log the complete redirect URI because it may contain a code.

## Token Storage

`TokenStore` is the sole persistent source of API credentials and uses a
non-exportable Android Keystore AES-GCM key. Storage is described only as
Keystore-backed. It is described as hardware-backed only when runtime `KeyInfo`
evidence confirms secure hardware on that tested device.

Encrypted state may contain:

- Access token.
- Refresh token.
- Token type.
- Expiry.
- Fixed canonical-origin token endpoint.
- Canonical backend origin and OAuth client ID.

It must not contain a client secret because none exists.

Required storage controls:

- Application-private files only.
- Store a version, fresh random 96-bit GCM IV, ciphertext, and 128-bit
  authentication tag for every encryption operation.
- Never reuse an IV with the same key.
- Use `AtomicFile`: encrypt first, write a complete replacement, then finish the
  atomic write. A failed replacement preserves the previous complete record or
  leaves no record.
- Reject unknown versions, malformed, truncated, partially written,
  authentication-tag-tampered, or ciphertext-tampered records.
- `TokenStore.read(expectedOrigin, expectedClientId)` returns credentials only
  on an exact canonical identity match.
- Treat authentication-tag failure or permanent key invalidation as signed-out
  state requiring manual recovery of any unresolved mutation.
- `android:allowBackup="false"`.
- Backup and device-transfer rules exclude the complete application data root.
- No token in `Bundle`, `SavedStateHandle`, logs, analytics, crash reports,
  screenshots, clipboard, notification, or QR code.
- Authentication state is cleared if the Keystore key becomes permanently
  invalidated; an unresolved mutation is not silently discarded.

Required tests cover:

- Unique IVs for repeated encryption of the same value.
- Independent IV, tag, and ciphertext tampering.
- Empty, truncated, malformed, and unknown-version records.
- Atomic replacement and injected partial-write failure.
- Binding to canonical origin and OAuth client.
- Backup and device-transfer exclusion.
- Logout, terminal invalidation, and permanent-key-invalidation cleanup.
- Mismatched unsolicited callback rejection while preserving the original
  pending attempt and expiry.

## Refresh and HTTP 401

- `MobilePosApiClient` receives `TokenStore` as an application-scoped
  dependency. UI and repository callers never supply bearer strings.
- The client attaches Bearer only after the request's scheme, host, and port
  exactly match the configured canonical origin.
- Redirect following is disabled. Credentials are never forwarded to another
  origin, redirect target, sibling host, suffix host, subdomain, or port.
- Authorization-code exchange and refresh use only
  `<canonical-origin>/api/method/frappe.integrations.oauth2.get_token`; an
  arbitrary absolute token URL, origin mismatch, or 30x response is rejected.
- Authorization uses only
  `<canonical-origin>/api/method/frappe.integrations.oauth2.authorize` with
  scope `all`. Discovery documents, dynamic endpoints, nested URLs, and
  cashier-editable OAuth URLs are rejected.
- Serialize refresh so concurrent calls do not issue multiple token requests.
- An eligible read, including read-only POST scan or quote, may perform one
  serialized refresh and one retry.
- A mutation is never automatically retried by the API client after HTTP 401.
- Mutation HTTP 401 returns a typed authentication-required result to
  `RecoveryCoordinator`.
- `RecoveryCoordinator` persists `auth_required` before refresh or browser work
  and preserves the original UUID, operation, origin, endpoint, and exact bytes.
- Attempt the approved refresh flow.
- If refresh fails, return to browser authorization.
- Resume only after bootstrap confirms the same cashier, canonical origin, and
  OAuth client.
- Standard mutations without a status endpoint resolve through replay of the
  exact persisted request and key.
- Closing with a known completed submit reference uses `closing.status`.
- Closing with an unknown submit result replays the exact persisted submit.
- Opening may additionally reconcile through documented `sessions.current`.
- The API client never independently reconstructs or blindly resubmits a
  mutation.
- A different cashier cannot replay another user's pending mutation.

## Logout

Normal logout is blocked while any mutation is `prepared`, `sending`,
`waiting_retry`, `auth_required`, `request_in_progress`, `closing_queued`, or
`manual_recovery`. A `completed` or `rejected` result must first be explicitly
acknowledged; that acknowledgment permits encrypted terminal-body deletion.

Logout performs:

- `TokenStore.clear()` and `OAuthAttemptStore.clear()`, including in-memory
  snapshots.
- In-memory bootstrap, opening input, customer, catalog, history, cart, receipt,
  return input, closing input/status, and navigation-state removal.
- Sensitive terminal-response deletion.
- Navigation to sign in.

When a mutation is unresolved, the app directs the cashier through
reauthentication and recovery before logout. Device reassignment with an
unresolved transaction is a manager/support stop condition; the app never
silently abandons or replays it as another user.

Server revocation remains a manager ERPNext Desk operation until an approved
no-secret public-client revocation contract exists.

## Canonical Backend Origin

Configuration, OAuth attempts, tokens, API requests, and pending mutations use
one canonical origin string:

1. Reject leading or trailing whitespace, control characters, backslashes, and
   malformed percent escapes.
2. Parse as a URI or OkHttp `HttpUrl`.
3. Lowercase scheme and host.
4. Require HTTPS. No cleartext local-debug exception is currently approved.
5. Require a non-empty host.
6. Reject username, password, query, fragment, and any path other than empty or
   `/`.
7. Reject a trailing-dot host and invalid port.
8. Remove HTTPS port 443.
9. Preserve another explicit valid port.
10. Serialize without a trailing slash.

Accepted examples:

| Input | Canonical result |
| --- | --- |
| `https://pos.example.com` | `https://pos.example.com` |
| `HTTPS://POS.EXAMPLE.COM/` | `https://pos.example.com` |
| `https://pos.example.com:443/` | `https://pos.example.com` |
| `https://pos.example.com:8443/` | `https://pos.example.com:8443` |

Rejected examples:

| Input | Reason |
| --- | --- |
| `http://pos.example.com` | Cleartext is not approved |
| `https://pos.example.com/api` | Path-based origin |
| `https://user@pos.example.com` | Userinfo |
| `https://pos.example.com?site=x` | Query |
| `https://pos.example.com/#callback` | Fragment |
| ` https://pos.example.com` | Surrounding whitespace |
| `https://pos.example.com.` | Ambiguous trailing-dot host |
| `https://pos.example.com:99999` | Invalid port |

Canonical values are compared byte-for-byte before any credential is read.
Fixed versioned endpoint paths are appended only after that match succeeds.

## Configuration

The base URL, client ID, and redirect URI are not secrets, but they must be:

- Exact per environment.
- Immutable during an authorization attempt.
- Bound to HTTPS.
- Excluded from cashier-editable free-form fields in production.
- Reviewed in the merged release manifest and resources.

Task 3 does not choose how these values are provisioned. Before implementation,
the user must approve the configuration source for the exercised build variant,
the exact public-client values, a non-production test cashier provisioned
outside the repository, and the 10-minute authorization-attempt lifetime. The
app never provisions the backend OAuth client or test cashier.

Production provisioning and distribution remain blocked until an approved
deployment model records how those values are supplied.

## Acceptance Criteria

- Authorization always sends a non-empty S256 challenge.
- No client secret or Basic token authentication exists in source or APK.
- Wrong state, redirect, verifier, client, user, or replayed code is rejected.
- Cold-start and warm-start redirect handling share validation.
- Tokens remain encrypted and excluded from backup.
- Refresh is serialized.
- HTTP 401 preserves pending mutation identity and body.
- Logout removes local credentials and sensitive caches.
- API access uses only the Mobile POS OAuth Client bearer token.
- Active OAuth attempts survive real process death and are single-use.
- Tokens are never attached outside their canonical origin.
- Eligible reads refresh at most once; mutation 401 is durably recoverable and
  never transparently replayed.
- API 23 and target API instrumentation tests cover redirect and Keystore
  behavior.

## Stop Conditions

- Exact redirect and provisioning values are not approved.
- The fixed authorization path, token path, scope, configuration source,
  non-production test cashier, or attempt lifetime is not approved.
- The exercised environment's App Link signing association is not approved or
  does not verify on the installed build.
- The backend OAuth Client is not provisioned with mandatory S256.
- A forged or duplicate callback can bypass exact URI, state, single-use, or
  PKCE validation.
- A library requires a client secret or cannot support API 23.
- Any credential appears in logs, backup, source, generated configuration, or
  APK inspection.
