# Prototype Top Bar Clock and Logout

## Scope

Update Android prototype shared POS top bar only. Production `app/` remains untouched.

## Design

- Remove logout icon and `onLogout` wiring from prototype `PosTopBar` and `PosShell`.
- Remove now-unused top-bar logout arguments from Cashier and History callers.
- Keep More screen logout action and callback unchanged; More no longer forwards it to `PosShell`.
- Change application clock format from `h:mm a` to `h:mm:ss a`.
- Refresh clock state every second so displayed seconds remain current.
- Leave Android system status-bar clock unchanged.

## Acceptance Criteria

- Cashier, History, and More top bars show storefront, title, schedule icon, and `h:mm:ss a` clock with no logout icon.
- More screen logout action still appears and invokes existing callback.
- No new dependency, asset, or production-app change.
- Prototype debug build and lint pass; emulator screenshot confirms top bar and seconds.

## Out Of Scope

- Android system status-bar clock format.
- Authentication behavior changes.
- Production `app/` navigation or top bar.
