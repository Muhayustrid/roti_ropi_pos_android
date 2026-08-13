# Prototype Bottom Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make prototype bottom navigation reusable, symmetric, centered, and visually clear by giving every tab an equal cell and filling the selected cell background.

**Architecture:** Keep `NavTab`, `PosBottomBar`, and `PosShell` as the shared navigation boundary. Replace separate active/inactive width behavior with one equal-weight tab-cell component; callers continue owning navigation callbacks. No screen-specific navigation logic or production-module changes.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, standalone `prototype/android-prototype` Gradle project, emulator `mobile-pos-api36`.

## Global Constraints

- Modify only `prototype/android-prototype/`; do not touch production `app/`.
- Keep `minSdk 26` and existing Compose/Material 3 dependencies.
- Preserve `NavTab` labels/icons and existing callback behavior.
- Do not commit or push without explicit user approval.
- Verify with `./gradlew :app:assembleDebug` from `prototype/android-prototype/`.

---

### Task 1: Refactor Shared Bottom Navigation

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosBottomBar.kt`
- Do not modify: `CashierScreen.kt`, `HistoryScreen.kt`, `OpeningBalanceScreen.kt`, or `Navigation.kt`

**Interfaces:**
- Consumes: existing `NavTab`, `selectedTab`, and `onTabSelected` values.
- Produces: unchanged `PosBottomBar(selectedTab, onTabSelected, modifier)` API.

- [ ] **Step 1: Replace separate active/inactive sizing with equal tab cells**

Keep `NavTab` unchanged. Replace `ActiveNavTab` and `InactiveNavTab` with one
private `RowScope` composable so `Modifier.weight(1f)` is valid:

```kotlin
@Composable
private fun RowScope.NavigationTab(
    tab: NavTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
```

Add these imports:

```kotlin
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
```

Remove unused `clickable` and old active/inactive helper imports.

- [ ] **Step 2: Make outer layout symmetric**

Change the `PosBottomBar` content row to this shape:

```kotlin
Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation = 8.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTab.entries.forEach { tab ->
            NavigationTab(
                tab = tab,
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
            )
        }
    }
}
```

This makes all three cells equal width because `NavigationTab` owns
`Modifier.weight(1f)`, centers icon and label using `fillMaxSize`, and fills
only the selected cell with `primaryContainer`.

- [ ] **Step 3: Compile the component change**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` with no Kotlin compilation errors.

### Task 2: Verify Shared Navigation on Device

**Files:**
- Verify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosBottomBar.kt`
- Verify callers: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/components/PosShell.kt`, `ui/cashier/CashierScreen.kt`, `ui/history/HistoryScreen.kt`, `ui/opening/OpeningBalanceScreen.kt`
- Evidence: `prototype/design-refs/navigation-verification/01-cashier.png`, `02-history.png`, `03-opening.png`

**Interfaces:**
- Consumes: debug APK from Task 1 and existing emulator `mobile-pos-api36`.
- Produces: screenshots and UI-layout evidence; no API changes.

- [ ] **Step 1: Install and launch the debug APK**

Run:

```bash
ADB=/Users/rotiropi/Library/Android/sdk/platform-tools/adb
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am force-stop com.rotiropi.pos_prototype
$ADB shell am start -n com.rotiropi.pos_prototype/.MainActivity
```

- [ ] **Step 2: Capture Cashier navigation state**

Reach Cashier through the existing sign-in/opening prototype flow, capture the
screen, and verify the UI tree has `Cashier`, `History`, and `More`. Confirm
Cashier active background spans its equal-width cell and icon/label centers do
not shift toward neighboring tabs.

- [ ] **Step 3: Tap History and capture the active-state transition**

Tap the center of the History cell. Verify `History` becomes the only filled
cell, all three cells retain equal bounds, and the existing History destination
opens. Capture `02-history.png`.

- [ ] **Step 4: Verify Opening screen reuse**

Return through the existing flow to Opening Balance. Verify Cashier remains the
selected tab, its cell is filled and centered, and no screen-specific duplicate
navigation bar exists. Capture `03-opening.png`.

- [ ] **Step 5: Final build and diff checks**

Run:

```bash
./gradlew :app:assembleDebug
git diff --check
```

Expected: build succeeds, `git diff --check` returns no whitespace errors, and
only the shared prototype navigation component plus verification evidence are
changed. Do not commit.
