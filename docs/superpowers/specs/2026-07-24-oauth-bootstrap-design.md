# Design Specification: Android Phase 2 (OAuth PKCE, Token Storage, Bootstrap, and Session State)

## 1. Executive Summary

Phase 2 implements secure cashier authentication and initial session setup for the Mobile POS Android application, fully compliant with ERPNext Mobile POS API Contract v1 and security rules in `AGENTS.md`.

Key requirements:
- OAuth 2.0 Authorization Code with mandatory PKCE (S256).
- Public OAuth client without embedded client secrets.
- Browser/Custom Tab authentication flow (no credential capture in WebView).
- Android Keystore-backed AES-256-GCM token storage, excluded from backups and logs.
- Single-use transient authorization state (`OAuthAttemptStore`) with a 10-minute expiration.
- Cashier bootstrap and active session reconciliation (`BootstrapRepository` & `SessionRepository`).

---

## 2. Architecture & Components

```
+---------------------------------------------------------------------------------+
|                                 UI Layer                                        |
|   SignInFragment ----> AuthViewModel ----> SessionViewModel / MainActivity      |
+------------------------------------+--------------------------------------------+
                                     |
                                     v
+---------------------------------------------------------------------------------+
|                             Domain / Repository Layer                           |
|                                                                                 |
|  +------------------------+  +-------------------------+  +------------------+  |
|  |     AuthRepository     |  |   BootstrapRepository   |  | SessionRepository|  |
|  +-----------+------------+  +------------+------------+  +--------+---------+  |
+--------------|----------------------------|------------------------|------------+
               |                            |                        |
               v                            v                        v
+---------------------------------------------------------------------------------+
|                               Data & Security Layer                             |
|                                                                                 |
|  +------------------------+  +-------------------------+  +------------------+  |
|  |    OAuthPkceHelper     |  |    OAuthAttemptStore    |  |    TokenStore    |  |
|  |  (Verifier & S256)     |  |   (State & Expiry)      |  | (Keystore AES)   |  |
|  +------------------------+  +-------------------------+  +------------------+  |
|                                                                                 |
|  +---------------------------------------------------------------------------+  |
|  |                           MobilePosApiClient                              |  |
|  |   - Redacted logging      - X-Idempotency-Key        - Bearer auth header |  |
|  +---------------------------------------------------------------------------+  |
+---------------------------------------------------------------------------------+
```

### 2.1 OAuth PKCE & Authorization State (Task 3)

#### 1. `OAuthPkceHelper`
- **Purpose:** Generates cryptographically secure high-entropy PKCE verifiers and SHA-256 challenges.
- **Implementation Details:**
  - Verifier: `SecureRandom` 32 to 64 random bytes, URL-safe Base64 encoded without padding (43-86 chars long, satisfying RFC 7636).
  - Challenge: SHA-256 hash of the `code_verifier`, URL-safe Base64 encoded without padding.
  - Challenge Method: Always `"S256"`.
  - State: High-entropy UUID / random string to prevent CSRF in redirect handling.

#### 2. `OAuthAttemptStore`
- **Purpose:** Securely holds transient authorization attempt parameters pending callback from system browser.
- **Fields:** `state`, `codeVerifier`, `redirectUri`, `createdAtTimestamp`.
- **Rules:**
  - 10-minute expiration window (`EXPIRATION_MS = 600,000`).
  - Single-use: once validated or consumed, the attempt is immediately cleared.
  - Concurrent attempt handling: starting a new auth attempt overwrites any existing attempt.

#### 3. `TokenStore` & `KeystoreTokenManager`
- **Purpose:** Encrypted local persistence of access and refresh tokens.
- **Encryption:**
  - Uses `AndroidKeyStore` provider to generate/retrieve a master key: AES 256-bit GCM mode (`AES/GCM/NoPadding`).
  - Key Alias: `rotiropi_pos_oauth_key`.
  - Storage target: Encrypted `SharedPreferences` (`pos_oauth_tokens.xml`) with random 12-byte IV per encryption operation.
- **Fields stored:**
  - `accessToken`: String (encrypted)
  - `refreshToken`: String (encrypted, optional/nullable)
  - `tokenType`: String (`"Bearer"`)
  - `expiresAtEpochMs`: Long (expiration timestamp)
- **Security Rules:**
  - Excluded from backups (`backup_rules.xml` and `data_extraction_rules.xml`).
  - Clear on logout: `clearTokens()` wipes all stored credentials.

#### 4. `OAuthRedirectActivity`
- **Purpose:** Deep-link target handling OAuth authorization code redirect.
- **Manifest Filter:**
  ```xml
  <activity
      android:name=".data.auth.OAuthRedirectActivity"
      android:exported="true"
      android:launchMode="singleTask">
      <intent-filter>
          <action android:name="android.intent.action.VIEW" />
          <category android:name="android.intent.category.DEFAULT" />
          <category android:name="android.intent.category.BROWSABLE" />
          <data
              android:scheme="rotiropi.pos"
              android:host="oauth"
              android:path="/callback" />
      </intent-filter>
  </activity>
  ```
- **Flow:**
  1. Receives intent with `rotiropi.pos://oauth/callback?code=...&state=...` (or `error=...`).
  2. Validates `state` against active `OAuthAttemptStore`.
  3. On success, extracts `code` and `code_verifier`, triggers token exchange via `AuthRepository`.
  4. Redirects flow back to `MainActivity` / `SignInFragment` with result.

---

### 2.2 Bootstrap & Session Management (Task 4)

#### 1. `BootstrapRepository`
- **Endpoints:**
  - `bootstrap.get_pos_profiles` -> list of assigned POS profiles for cashier.
  - `bootstrap.get_bootstrap_data` -> outlet profile, warehouse, company, payment method mappings, capabilities.
- **Responsibilities:**
  - Fetches profiles and populates selection UI if cashier has multiple profiles.
  - Caches current profile configuration locally.

#### 2. `SessionRepository`
- **Endpoints:**
  - `session.get_current_session` -> active POS Opening Entry status (`status="Open"`).
  - `session.open_session` -> creates a new POS Opening Entry.
- **Responsibilities:**
  - Tracks active opening entry (`opening_entry_id`, `posting_date`, `period_start_date`).
  - Handles `STALE_OPENING` warnings gracefully.
  - Exposes state flow for UI binding (Signed out -> Authenticated -> Profile Selected -> Opening Entry Active).

---

## 3. Data Transfer Objects (DTOs) & Contracts

- Update `SessionDtos.kt` to include:
  - `OAuthTokenRequestDto` (`grant_type="authorization_code"`, `client_id`, `code`, `redirect_uri`, `code_verifier`).
  - `OAuthTokenResponseDto` (`access_token`, `refresh_token`, `expires_in`, `token_type`, `scope`).
  - `OAuthRefreshTokenRequestDto` (`grant_type="refresh_token"`, `client_id`, `refresh_token`).

---

## 4. Testing Strategy (TDD Approach)

1. **`OAuthPkceHelperTest`**:
   - Verify `code_verifier` length is between 43 and 128 chars, URL-safe Base64 unpadded.
   - Verify SHA-256 challenge generation against known test vector.
2. **`OAuthAttemptStoreTest`**:
   - Verify single-use retrieval.
   - Verify expiration after 10 minutes.
   - Verify state mismatch rejection.
3. **`TokenStoreTest`**:
   - Verify encryption/decryption round-trip.
   - Verify clearing tokens wipes state completely.
4. **`OAuthRedirectActivityTest` & `AuthRepositoryTest`**:
   - MockWebServer tests for `/api/method/frappe.integrations.oauth2.get_token`.
   - Verify code exchange payload structure (no client secret sent).
5. **`BootstrapRepositoryTest` & `SessionRepositoryTest`**:
   - Test DTO parsing and profile selection logic.

---

## 5. User Review & Self-Review Checklist

- [x] No client secret used or referenced anywhere.
- [x] WebView usage prohibited; system browser Custom Tab used.
- [x] Tokens stored securely using Keystore-backed AES encryption.
- [x] `allowBackup="false"` and data extraction rules verified.
- [x] Minimum SDK 23 compliance strictly preserved.
