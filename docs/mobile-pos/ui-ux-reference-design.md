# Mobile POS UI/UX Reference Design

## Purpose

This document defines the approved visual and interaction direction for the native Android Mobile POS client. It adapts useful UI patterns from a pinned open-source reference while preserving the Android architecture, Mobile POS v1 contract, and ERPNext authority.

It does not approve source copying, backend changes, new business rules, or implementation beyond the task named in [`implementation-plan.md`](implementation-plan.md).

## Reference Provenance

- Repository: [`jipraks/kasirgratisan`](https://github.com/jipraks/kasirgratisan)
- Inspected branch: `main`
- Inspected commit: [`25c244027d7b9723f1b53a71649630e020e63413`](https://github.com/jipraks/kasirgratisan/tree/25c244027d7b9723f1b53a71649630e020e63413)
- Commit date: 2026-07-13
- License: MIT
- Copyright notice: `Copyright (c) 2025 KasirGratisan`

The reference was inspected through immutable GitHub API and raw-file URLs pinned to the commit above. No reference repository content was cloned into this Android repository.

## Reference File Map

| UI responsibility | Reference file |
| --- | --- |
| Semantic tokens, font, radius, light/dark palettes | `src/index.css` |
| Tailwind semantic mapping | `tailwind.config.ts` |
| Accent selection | `src/hooks/use-theme-color.ts`, `src/components/ThemeColorPicker.tsx` |
| App shell | `src/components/layout/AppLayout.tsx` |
| Bottom navigation | `src/components/layout/BottomNav.tsx` |
| Dashboard | `src/pages/Dashboard.tsx` |
| Cashier, product cards, cart, and checkout | `src/pages/Cashier.tsx` |
| Product lookup | `src/components/ProductPicker.tsx` |
| Products | `src/pages/Products.tsx` |
| Reports | `src/pages/Reports.tsx`, `src/components/reports/*` |
| Settings and theme settings | `src/pages/Settings.tsx`, `src/pages/settings/ThemeSettings.tsx` |

## Allowed Adaptation

Adapt visual hierarchy and interaction flow only:

- Mobile-first composition.
- Neutral application background and elevated rounded surfaces.
- Semantic light and dark themes.
- Configurable primary accent.
- Compact KPI cards and warning banners.
- Fixed safe-area-aware bottom navigation.
- Elevated circular Cashier center action.
- Search, manual barcode entry, horizontal category chips, and adaptive product grids.
- Phone cart summary and bottom sheet.
- Tablet and expanded-landscape persistent cart pane.
- Grouped settings rows.
- Compact charts and ranked lists.
- Consistent spacing, typography, shape, elevation, icon, and press feedback.

## Explicitly Excluded Reference Behavior

Do not copy or recreate:

- React, Vite, Tailwind, shadcn/Radix, Capacitor, or PWA runtime code.
- Dexie, IndexedDB, local transaction database, live queries, or offline accounting.
- Reference authentication, user permissions, onboarding, activation, cloud backup, or synchronization.
- Local product, stock, customer, invoice, payment, debt, expense, or report business logic.
- Product CRUD, Customer mutation, open bills, debt flows, or local stock mutation.
- Export, printing, or analytics persistence.
- Reference branding, logos, illustrations, product images, or proprietary data.
- Unlicensed fonts, icons, screenshots, or copied component source.

## Android Design Tokens

### Color

Use semantic Compose tokens. Exact accessible values are finalized and tested in Task 2B.

| Token | Intent |
| --- | --- |
| `background` | Light gray application canvas; near-black neutral in dark mode |
| `surface` | White card/dialog surface; elevated dark neutral in dark mode |
| `surfaceVariant` | Muted controls, image placeholders, and secondary panels |
| `primary` | Configurable main action and selected state |
| `accent` | Secondary highlight; reference intent is teal |
| `success` | Confirmed positive state with icon/text, never color alone |
| `warning` | Recoverable warning or attention state with icon/text |
| `destructive` | Terminal or destructive state with icon/text |
| `outline` | Low-contrast separators and input borders |

The reference uses a blue primary, teal accent, and semantic green/orange/red states. Android may adjust values to meet WCAG-oriented Android contrast requirements in light and dark themes.

### Typography

Preferred family: Plus Jakarta Sans, weights 400 through 800.

- Bundle font files only from a pinned OFL-licensed source.
- Include the OFL license notice in the repository.
- Verify every required weight on API 23 and API 36.
- Use the closest bundled Android sans-serif fallback if the preferred package fails verification.
- Respect system font scaling through at least 1.5.

Typography hierarchy:

- Screen title: strong, compact headline.
- KPI value and payable/receipt total: prominent display or headline.
- Card title and product name: medium-to-semibold.
- Supporting metadata: smaller muted text, never below accessible practical size.
- Buttons and navigation labels: concise and stable under font scaling.

### Shape, Spacing, and Elevation

- Base card radius: approximately 12 dp.
- Dialog and sheet radius: 16–24 dp where Material 3 patterns require it.
- Touch target: at least 48 dp.
- Screen horizontal padding: 16 dp on compact width; larger bounded gutters on expanded width.
- Section rhythm: 16–20 dp.
- Card internal spacing: 12–16 dp.
- Use restrained elevation and tonal surfaces; do not reproduce web hover-only behavior.
- Use press/ripple feedback and semantic selected states instead of pointer hover assumptions.

### Icons and Assets

- Use Material Icons or another Android icon set with a compatible license.
- Add content descriptions or semantic labels when an icon has meaning.
- Decorative icons are hidden from accessibility services.
- Product images use neutral placeholders until a measured, approved image-loading need exists.
- Do not copy reference branding assets.

## App Shell and Navigation

Root destinations:

1. Home
2. Products
3. Cashier
4. Reports
5. More

Behavior:

- Bottom navigation remains fixed and applies navigation-bar and display-cutout insets.
- Cashier is a circular center action elevated above the bar.
- Selected destinations use primary color, icon container, label, and semantics; color is not the only indicator.
- Each root tab preserves its back stack and avoids duplicate destinations.
- Auth, profile, opening, recovery, payment, receipt, return, and closing are flow destinations outside or above the root tab shell as appropriate.
- Content is never obscured by the bottom bar, keyboard, system navigation, or floating cart summary.

## Adaptive Layout

### Compact Phone

- Single-column content.
- Product grid uses two columns where width permits.
- Cashier cart appears as a floating summary above bottom navigation and opens a modal bottom sheet.
- Dialogs stay within viewport width and height and remain scrollable.

### Tablet and Expanded Landscape

- Content width uses larger bounded gutters rather than stretching cards indefinitely.
- Product grid grows to three or four columns based on measured width.
- Cashier uses product content and a persistent 320–384 dp-class cart pane.
- Dashboard summary cards can expand from two to four columns.
- Navigation remains bottom-based unless a later measured usability task explicitly approves another adaptive navigation pattern.

### Orientation and Input

- Do not lock orientation.
- Preserve current tab, query, category, and unsubmitted cart state through configuration changes.
- Support hardware keyboard traversal and HID/keyboard-wedge scanner input.
- Camera capture is excluded until separately approved.

## Screen Designs

### Dashboard

Sections:

- Store or selected POS Profile identity.
- Today's sales KPI slot.
- Transaction-count KPI slot.
- Quick actions controlled by server capabilities.
- Recent scoped transactions.
- Low-stock alert slot.

States:

- Loading.
- Profile unavailable.
- Partial live data.
- Offline.
- Error with Retry.
- Debug-only populated preview marked `Demo data`.

Data boundary:

- Store identity may come from bootstrap/profile data after Task 4.
- Quick actions come from bootstrap capabilities.
- Recent transactions may later come from bounded `sales.list` results.
- Current endpoints do not provide complete daily sales, complete transaction counts, or complete low-stock aggregates.
- A bounded page must not be summed and labeled as a complete daily KPI.
- Unsupported release sections are hidden or explicitly marked unavailable.

### Cashier

Sections:

- Product search.
- Manual/HID barcode value action.
- Horizontal category chips.
- Adaptive product grid.
- Product cards with image placeholder, name, price-list snapshot, and stock snapshot.
- Floating cart summary on phone.
- Persistent cart pane on expanded width.
- Cart bottom sheet on phone.
- Quantity controls and row warnings.
- Customer picker when Task 7 is integrated.
- Payment dialog and server receipt when Task 9 is integrated.

Interaction:

1. Search, scan, or choose a category.
2. Select an item.
3. Obtain the required server quote before adding or updating the cart.
4. Adjust quantity through accessible controls.
5. Open the cart summary or use the persistent cart pane.
6. Continue only when server capabilities and contract gates allow payment.
7. Submit through durable recovery, never directly from a composable.
8. Render receipt only from terminal server data.

Business limits:

- Cart remains bounded to 50 distinct rows.
- Price and stock are server snapshots, not guarantees.
- Authoritative tax and payable totals are not calculated locally.
- Discount editing is absent or disabled until a versioned contract exists; server-returned discounts are display-only.
- Exact settlement only.
- Overpayment input and local change calculation are absent.
- Receipt may display server-returned `change_amount`.

### Products

Sections:

- Search.
- Horizontal or compact category filtering.
- List/grid selection if both modes remain justified after usability review.
- Item image placeholder.
- Item name and code.
- Item Group.
- Price List and Item Price snapshot.
- Warehouse stock snapshot.
- Detail screen or sheet.

ERPNext mapping:

- Item.
- Item Group.
- Price List and Item Price.
- Warehouse.
- Stock availability from approved catalog responses.

Products is read-only. Product CRUD, image persistence, stock management, import, and delete actions from the reference are excluded.

### Reports

Sections:

- Period tabs.
- Sales and transaction KPI slots.
- Semantic breakdown rows.
- Compact chart.
- Top-selling-product list.

Current release behavior:

- Render an honest unavailable state because the existing 14 endpoints do not provide complete report aggregates.
- Keep populated visual compositions in debug previews marked `Demo data`.
- Do not derive complete reports from a bounded history page.
- Export and printing remain excluded.

Live reports require a separately approved backend contract and Android integration task.

### More and Settings

Groups:

- Outlet or POS Profile information.
- User and session information.
- Theme mode and accent.
- Printer status when supported.
- Synchronization status when supported.

Behavior:

- Theme mode and accent are non-sensitive local preferences and may use application-private `SharedPreferences`.
- Outlet and user/session data become live after bootstrap integration.
- Printer and synchronization rows are hidden or disabled with `Not supported` until an approved implementation exists.
- Group rows use icon, title, subtitle, and trailing action with consistent rhythm.

## State and Preview Policy

Every major screen defines immutable UI states for:

- Loading.
- Empty.
- Populated when integrated.
- Offline.
- Unavailable.
- Recoverable error.
- Terminal error where relevant.

Rules:

- ViewModels own state and events.
- Composables accept immutable state and callbacks.
- Debug previews and test fixtures may use synthetic records.
- Populated debug previews display `Demo data` persistently.
- Release builds package no preview fixtures or fake ERPNext records.
- Unintegrated release screens show unavailable state, not mock content.
- UI state never implies server acceptance without a terminal persisted response.

## Accessibility Requirements

- Minimum 48 dp touch targets.
- Visible labels or content descriptions for actionable controls.
- Semantic heading, role, selected state, progress, error, and state descriptions.
- Logical TalkBack and keyboard focus order.
- Error announcements and accessible Retry actions.
- No status communicated by color alone.
- Font scale 1.5 without clipped primary actions or hidden totals.
- Portrait and landscape support.
- External keyboard and scanner input support.
- Charts include textual summaries and labels.

## Verification Matrix

Task 2B and each later integrated feature verify:

- Light and dark themes.
- Every supported accent.
- Compact phone portrait and landscape.
- Tablet/expanded portrait and landscape.
- Font scale 1.0 and 1.5.
- API 23 and API 36.
- Safe-area and keyboard insets.
- TalkBack and external keyboard focus.
- Phone cart sheet and expanded cart pane.
- Loading, empty, offline, unavailable, error, and populated integrated states.
- Release exclusion of debug fixtures.
- No unsupported endpoint, local authoritative accounting, hidden mutation retry, or copied reference runtime behavior.

## Approval Boundary

This design approves UI direction and documentation alignment only. It does not authorize Compose source, Gradle, test, commit, push, backend, deployment, or production configuration changes. Those actions follow the serial tasks and approval stops in [`implementation-plan.md`](implementation-plan.md).
