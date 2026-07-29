# Backend Contract Snapshot

This directory contains a reviewed snapshot of the Mobile POS backend documents needed by Android implementation agents.

## Authority

Canonical source repository:

```text
https://github.com/Muhayustrid/roti_ropi_pos
```

Canonical source directory:

```text
docs/mobile-pos/
```

Snapshot source:

```text
Commit: b2a09d2
Date: 2026-07-29
```

Backend source wins whenever this snapshot conflicts with backend runtime code or newer backend documentation. Android-specific behavior remains owned by documents one directory above this snapshot.

## Snapshot Files

- `android-backend-handoff.md`
- `api-contract.md`
- `authentication.md`
- `idempotency-and-recovery.md`
- `product-requirements.md`

## Refresh Rules

1. Update backend contracts in `roti_ropi_pos` first.
2. Commit and verify backend changes independently.
3. Copy all five snapshot files from one backend commit.
4. Review Android compatibility and cross-document links.
5. Update commit and date above.
6. Review and commit Android documentation independently.

Do not edit backend contracts only in this snapshot. Do not copy Android-specific authentication, UI, storage, or recovery decisions back into backend contracts.
