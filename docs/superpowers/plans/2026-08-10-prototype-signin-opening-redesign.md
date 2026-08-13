# Prototype Sign-In to Opening Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh prototype screens 1.2-1.5 to match the latest Stitch references while preserving the existing sign-in-to-opening callbacks and simulated flow.

**Architecture:** Keep the current four Composable entry points and `Navigation.kt` wiring. Apply visual changes inside the existing screen files, reuse Material 3 and existing theme tokens, and remove only the pre-shift bottom navigation that has no active behavior on Opening Balance.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, existing prototype theme, Gradle Android plugin.

## Global Constraints

- Change only `prototype/android-prototype/`; do not modify production `app/`.
- Keep `SignInScreen` 1.1 unchanged.
- Keep callbacks, simulated delays, navigation keys, authentication, persistence, API contracts, and accounting behavior unchanged.
- Use Stitch project `7730357639584129534` and screens `804fee3435cf4e6db85f40d85a166fdb`, `f4fe0e61b28b49bcb88bffa32e74d829`, `bbce56b6ed384b6a8fb77bc16aae5353`, and `d193685e19be40a4a78336c992e85b92` as visual references.
- Reuse installed dependencies and existing Material 3 primitives; add no dependency or new state host.
- Keep interactive targets at least 48dp and keep narrow-phone content scrollable.
- Do not commit or push without explicit user approval.

---

### Task 1: Refresh Login Waiting State

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/auth/LoginWaitingScreen.kt:52-287`

**Interfaces:**
- Consumes existing `onAuthSuccess: () -> Unit` and `onCancel: () -> Unit`.
- Produces the same `LoginWaitingScreen` entry point for `Navigation.kt`.

- [ ] **Step 1: Preserve waiting transition behavior before visual edits**

Keep this effect unchanged:

```kotlin
LaunchedEffect(Unit) {
    delay(4000)
    onAuthSuccess()
}
```

The screen must still return to sign-in only through `onCancel`.

- [ ] **Step 2: Align waiting composition to Stitch 1.2**

Keep the disabled sign-in card behind a scrim, then render one centered waiting
surface. Retain the existing component split and update spacing/copy/shape only:

```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
    contentAlignment = Alignment.Center,
) {
    WaitingDialog(onCancel = onCancel)
}
```

`WaitingDialog` must contain the sync progress indicator, `Waiting for ERPNext
sign-in...`, the browser-authentication explanation, and a full-width `Cancel`
button with a minimum height of 48dp. Keep the disabled ERPNext URL form and
`Roti Ropi` header as visual context.

- [ ] **Step 3: Compile the screen**

Run from `prototype/android-prototype/`:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 2: Refresh Opening Balance

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/opening/OpeningBalanceScreen.kt:60-155`

**Interfaces:**
- Consumes existing `onConfirm: () -> Unit` and `onLogout: () -> Unit`.
- Produces the same `OpeningBalanceScreen` entry point and existing amount state.

- [ ] **Step 1: Remove pre-shift bottom navigation**

Delete the `PosBottomBar` call and its `NavTab` import. Replace the current
bottom bar with only the sticky action surface:

```kotlin
bottomBar = {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Text("Start Shift", fontWeight = FontWeight.Bold)
        }
    }
},
```

- [ ] **Step 2: Match Stitch 1.3 hierarchy without changing amount logic**

Keep the existing title header, wallet icon, explanatory copy, session details,
Cash field, disabled QRIS field, and total summary. Keep these values and
transformations unchanged:

```kotlin
var cashAmount by remember { mutableStateOf("200000") }
val qrisAmount = "0"
val total = (cashAmount.toLongOrNull() ?: 0L) + (qrisAmount.toLongOrNull() ?: 0L)
```

Keep the vertical scroll modifier and use the existing Material 3 surface,
spacing, and typography tokens. Do not add a new opening state model.

- [ ] **Step 3: Compile the screen**

Run:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 3: Refresh Confirm Opening Balance

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/opening/ConfirmOpeningScreen.kt:39-236`

**Interfaces:**
- Consumes existing `onDismiss: () -> Unit` and `onRetry: () -> Unit`.
- Produces the same `ConfirmOpeningScreen` entry point for `Navigation.kt`.

- [ ] **Step 1: Make modal content scroll-safe**

Keep the existing scrim and centered `Surface`, but cap the modal content height
and scroll its internal column so small screens do not hide either action. Add
the height modifier before existing horizontal padding:

```diff
     modifier = Modifier
         .widthIn(max = 400.dp)
         .fillMaxWidth()
+        .heightIn(max = 780.dp)
         .padding(horizontal = 16.dp),
```

Change the existing confirmation column opening line from
`Column(modifier = Modifier.padding(24.dp)) {` to:

```diff
- Column(modifier = Modifier.padding(24.dp)) {
+ Column(
+     modifier = Modifier
+         .verticalScroll(rememberScrollState())
+         .padding(24.dp),
+ ) {
```

Do not remove the current icon, warning, rows, payment breakdown, total, or
action children.

- [ ] **Step 2: Apply latest Stitch copy and fixture values**

Replace only the visible fixture strings:

```kotlin
ConfirmRow("Cashier", "John Doe")
ConfirmRow("Profile", "Admin")
ConfirmRow("Outlet", "Downtown Store")
ConfirmRow("Currency", "IDR")
```

Keep `Cash` as `Rp 200.000` and `QRIS` as `Rp 0`. Change the primary label to
`Confirm & Start Shift`; keep its click handler as `onRetry`. Keep `Edit Amounts`
bound to `onDismiss`.

- [ ] **Step 3: Preserve modal accessibility**

Keep visible labels for all rows, maintain 48dp minimum action heights, and use
null content descriptions for decorative icons. The warning text must remain
readable over its tonal container and not rely on color alone.

- [ ] **Step 4: Compile the screen**

Run:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 4: Refresh Opening Recovery State

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/opening/OpeningRecoveryScreen.kt:35-94`

**Interfaces:**
- Consumes existing `onRetry: () -> Unit`.
- Produces the same `OpeningRecoveryScreen` entry point and delay behavior.

- [ ] **Step 1: Preserve the existing simulated recovery callback**

Keep this effect unchanged:

```kotlin
LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(5000)
    onRetry()
}
```

- [ ] **Step 2: Add the Stitch 1.5 branded header**

Render a compact top header before the status body with a menu icon, `Roti Ropi`
brand text, and account icon. These icons are decorative in the prototype and
must not introduce new callbacks.

- [ ] **Step 3: Replace the minimal body with the pending status hierarchy**

Keep the centered layout and use the existing Material 3 tokens. The body must
include the progress/sync indicator, this heading and copy, and the pending
status line:

```kotlin
Text(
    text = "Checking Shift Status",
    style = MaterialTheme.typography.headlineMedium,
    fontWeight = FontWeight.Bold,
)
Text(
    text = "Your Opening request was sent. We're checking the result before continuing.",
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
Text(
    text = "Starting Shift...",
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
)
```

- [ ] **Step 4: Compile the screen**

Run:

```text
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

---

### Task 5: Build and Emulator Verification

**Files:**
- Create directory: `prototype/design-refs/signin-opening-verification/`
- Create screenshots: `01-login-waiting.png`, `02-opening-balance.png`, `03-confirm-opening.png`, `04-opening-recovery.png`

**Interfaces:**
- Uses the unchanged `Navigation.kt` flow and the four updated screens.
- Produces build output, UI dump evidence, and visual comparison screenshots.

- [ ] **Step 1: Run static verification**

Run from `prototype/android-prototype/`:

```text
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

Expected: assemble and lint succeed. Unit-test failure from
`app/src/test/java/com/example/posprototype/ui/main/MainScreenViewModelTest.kt`
is a known unrelated scaffold issue if its missing symbols remain.

- [ ] **Step 2: Install and launch the prototype**

Run:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.rotiropi.pos_prototype
adb shell am start -n com.rotiropi.pos_prototype/.MainActivity
```

- [ ] **Step 3: Verify the complete path**

Use the existing emulator to verify:

1. Tap `Continue with ERPNext`; confirm 1.2 shows disabled sign-in context,
   scrim, waiting indicator, and `Cancel`.
2. Tap `Cancel`; confirm sign-in returns. Start again and wait for opening.
3. Confirm 1.3 has no bottom navigation, Cash is editable, QRIS is disabled,
   and `Start Shift` opens 1.4.
4. Confirm 1.4 shows John Doe, Admin, Downtown Store, latest button copy, and
   `Edit Amounts` returns to 1.3.
5. Confirm again; verify 1.5 shows branded header, `Checking Shift Status`,
   `Starting Shift...`, and auto-advances through the existing callback.

- [ ] **Step 4: Capture evidence and inspect semantics**

Capture the four screenshots in the listed directory. For each screen run:

```text
adb shell uiautomator dump /sdcard/ui.xml
adb pull /sdcard/ui.xml /tmp/signin-opening-ui.xml
```

Confirm expected text is present and inspect screenshots for clipping, action
visibility, scrim contrast, and system-bar overlap.

- [ ] **Step 5: Inspect intended diff**

Run:

```text
git diff --check
git status --short -- prototype docs/superpowers/specs/2026-08-10-prototype-signin-opening-redesign.md docs/superpowers/plans/2026-08-10-prototype-signin-opening-redesign.md
```

Expected: redesign-related additions are limited to the four prototype screen
files, the approved spec/plan, and verification screenshots. Existing unrelated
worktree changes may remain; do not revert them.
