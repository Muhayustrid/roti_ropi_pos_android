# Prototype Remove General Gear Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove trailing gear icons from all General settings rows on the prototype More screen.

**Architecture:** Keep `MoreAction` row structure, labels, values, spacing, and navigation unchanged. Delete only trailing Settings icon composition from the shared helper used by all three General rows.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Gradle Android build, API 25 emulator.

## Global Constraints

- Modify `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more/MoreScreen.kt`.
- Remove only the trailing `Settings` icon from `MoreAction`.
- Keep Language, English value, Printer Settings, Help & Support, row spacing, and navigation unchanged.
- Do not change production `app/` code or add dependencies.
- Do not commit or push unless separately authorized.

---

### Task 1: Remove General Trailing Gear

**Files:**
- Modify: `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more/MoreScreen.kt:173-181`
- Test: Manual API 25 More screen screenshot.

**Interfaces:**
- Consumes: Existing `MoreAction(icon, label, value)` calls.
- Produces: Same rows without trailing gear icons.

- [ ] **Step 1: Delete only trailing Settings icon**

Keep `MoreAction` as:

```kotlin
@Composable
private fun MoreAction(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

Do not modify `GeneralCard` labels, values, or row callbacks.

- [ ] **Step 2: Compile and lint**

Run from `prototype/android-prototype`:

```bash
./gradlew :app:assembleDebug :app:lintDebug
```

Expected: `BUILD SUCCESSFUL` and no new lint errors.

- [ ] **Step 3: Install and verify More screen**

Install latest APK:

```bash
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 install -r "app/build/outputs/apk/debug/app-debug.apk"
"/Users/rotiropi/Library/Android/sdk/platform-tools/adb" -s emulator-5554 shell am start -W -n com.rotiropi.pos_prototype/.MainActivity
```

Open More and confirm Language, Printer Settings, and Help & Support show no trailing gear; Language still shows English and row spacing remains unchanged.
