# Prototype Bottom Navigation Design

## Scope

Refine reusable bottom navigation for the standalone Android prototype at
`prototype/android-prototype/`. Keep existing `NavTab`, `PosBottomBar`, and
`PosShell` navigation callbacks. Apply visual changes through the shared
component so Cashier, History, and Opening screens stay consistent.

## Goals

- Give Cashier, History, and More equal-width navigation cells.
- Center each icon and label within its cell.
- Make the selected tab background fill its complete cell.
- Preserve existing tab routing and no-op behavior where callers provide it.
- Provide normal pressed, focused, and pointer-hover feedback through the
  existing Compose interaction surface.
- Keep mobile safe-area handling in the existing shell.

## Component Contract

`PosBottomBar` remains the shared entry point:

```kotlin
@Composable
fun PosBottomBar(
    selectedTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
)
```

No screen-specific navigation logic moves into the component. `PosShell`
continues to supply the selected tab and callback.

## Layout

- Outer surface fills available width and keeps rounded top corners.
- Outer row uses horizontal 12dp and vertical 8dp padding.
- Each tab uses `Modifier.weight(1f)` and a 64dp minimum-height target.
- Active and inactive tabs share the same cell bounds, so switching tabs does
  not shift neighboring content.
- Active cell uses `primaryContainer`, `onPrimaryContainer`, and a 20dp shape.
- Inactive cells use transparent background and `onSurfaceVariant` content.
- Icon and label are stacked and centered in every cell.
- Clickable cells retain Compose ripple/state-layer feedback for pressed,
  focused, and pointer-hover interactions.

## Non-Goals

- No new navigation destinations.
- No changes to `NavTab` labels or icons.
- No changes to Cashier, History, or Opening navigation callbacks.
- No production `app/` module changes.

## Verification

- Run `./gradlew :app:assembleDebug` from `prototype/android-prototype/`.
- Install the debug APK on `mobile-pos-api36`.
- Verify Cashier, History, and Opening all render equal-width centered cells.
- Verify active background fills exactly one cell and does not move neighbors.
- Verify tab callbacks still route Cashier ↔ History and More remains its
  existing no-op where configured.
- Capture an emulator screenshot for visual review.
