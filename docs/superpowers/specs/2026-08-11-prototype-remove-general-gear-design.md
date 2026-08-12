# Prototype Remove General Gear Design

## Goal

Remove trailing gear icons from General settings rows on the prototype More screen.

## Scope

- Modify `prototype/android-prototype/app/src/main/java/com/rotiropi/pos_prototype/ui/more/MoreScreen.kt`.
- Remove only the trailing `Settings` icon from `MoreAction`.
- Keep Language, English value, Printer Settings, Help & Support, row spacing, and navigation unchanged.
- Do not change production `app/` code or add dependencies.

## Verification

- Compile the prototype debug APK.
- Run lint.
- Open More on API 25 and confirm all three General rows have no trailing gear while labels and values remain.
