# POSERPNext Android Rules

These permanent rules apply to all AI coding agents and developers working in this repository.

## Purpose and Repository Boundary

- This repository contains the native Android client for Roti Ropi Mobile POS.
- Work only inside this Android repository unless the user explicitly approves another repository.
- Do not modify `roti_ropi_pos`, `bakery_manufacturing`, ERPNext, Frappe, or another repository from an Android task.
- The backend gateway is complete. Android consumes its versioned Mobile POS API and does not replace backend business logic.
- ERPNext remains authoritative for profiles, customers, pricing, discounts, taxes, stock, warehouses, UOM conversion, batches, serials, payments, totals, document state, and accounting.
- Do not silently change the Android contract to compensate for a presumed backend defect. Report a contract mismatch and stop the affected work.

## Communication and Repository Writing

- Communicate with the user in Bahasa Indonesia.
- Write repository Markdown, source code, comments, tests, technical identifiers, and commit messages in English.
- Keep permanent rules in `AGENTS.md` and Claude-specific workflow in `CLAUDE.md`.
- Keep task status, progress, and verification evidence only in `docs/mobile-pos/implementation-plan.md`.
- Do not copy daily progress or temporary session notes into instruction files.

## Sources of Truth

Never guess implementation status. Use this evidence order:

1. Current source code and executable tests.
2. Fresh focused and required verification results.
3. Current Git branch, status, and relevant diff.
4. `docs/mobile-pos/implementation-plan.md`.
5. Checked-in backend contract and Android handoff documents.

Additional rules:

- Source code and executable tests take precedence over Graphify, code graphs, generated reports, memory summaries, commit subjects, and stale documentation.
- The implementation plan's verified status summary and active task section define project progress. Unchecked execution-step boxes alone do not define status.
- `docs/mobile-pos/backend/api-contract.md` and `docs/mobile-pos/backend/android-backend-handoff.md` are the checked-in Android integration contract.
- The backend snapshot baseline is backend commit `b2a09d2`; use `docs/mobile-pos/backend/README.md` for provenance.
- Do not open the backend repository merely to resolve Android uncertainty. Report missing or conflicting local contract information.

## Android Architecture Boundary

- Use Kotlin, Jetpack Compose, and Material 3 for current and future UI work.
- Preserve `minSdk 23`, `targetSdk 36`, and namespace/application ID `com.rotiropi.pos_erpnext` unless a separately approved task changes them.
- Keep one native Android application. Do not introduce React, WebView application shells, PWA, Capacitor, or reference-repository runtime code.
- Prefer Android platform and AndroidX APIs. Add dependencies only for a demonstrated current need.
- Keep network DTOs separate from domain models and immutable UI state.
- Use structured concurrency and lifecycle-aware cancellation.
- Composables render state and emit events; ViewModels and repositories own orchestration.
- Keep synthetic data in debug previews or test source sets only. Release runtime must not present mock data as ERPNext integration.
- Call only documented Mobile POS API endpoints.
- Do not calculate or persist authoritative accounting totals locally.
- Do not create permanent walk-in Customers, implement an offline ledger, or infer server acceptance from local state.
- Design bounded, paginated, adaptive flows for API 23, low-end devices, phone/tablet, portrait/landscape, font scaling, TalkBack, and external keyboard/scanner input.
- Keep overpayment input, local change calculation, editable discounts, camera scanning, printer integration, and synchronization UI disabled or absent until separately approved.
- Do not derive complete Dashboard or Reports aggregates from a bounded API page.

## Security and Recovery

- Use OAuth 2.0 Authorization Code with mandatory PKCE S256 as a public client through a system browser or secure Custom Tab.
- Never embed administrator credentials, permanent API secrets, privileged API keys, shared cashier credentials, or a client secret.
- Use HTTPS only. Do not bypass certificate, trust-manager, or hostname verification.
- Store sensitive durable state using the documented Keystore-backed design.
- Never log passwords, tokens, cookies, authorization headers, OAuth codes/verifiers, or sensitive business data.
- Generate one lowercase UUID `X-Idempotency-Key` per logical mutation.
- Persist the exact request before sending and reuse the same key and bytes when the outcome is unknown.
- Follow `docs/mobile-pos/state-and-recovery.md` for retries, duplicate prevention, process death, authentication recovery, and terminal acknowledgment.

## Task Scope, Approval, and Progress

- Execute tasks serially in the order defined by `docs/mobile-pos/implementation-plan.md`.
- Determine the active task from verified evidence, not memory or unchecked boxes.
- Before implementation, report the branch, working-tree state, verified prior task, active task, evidence, likely files, tests, approach, and risks.
- Wait for explicit user approval before changing implementation code.
- Approval covers only the named task and scope. It does not authorize the next task, phase, commit, push, deployment, or backend change.
- After an approved task reaches a verified milestone or completion, update `docs/mobile-pos/implementation-plan.md` with concise factual status and evidence.
- Updating progress for the just-approved task is part of its completion workflow, but it never authorizes the next task.
- Do not mark a task complete without current source, tests, required verification, and intended-diff evidence.
- Preserve task requirements, steps, gates, and acceptance criteria when updating progress. Update only status and factual audit evidence unless the user separately approves plan changes.
- A lightweight documentation subagent may write the progress update, but the primary implementation/review model remains responsible for the completion verdict.

## Development and Verification

- Use test-driven development where practical, especially for authentication, parsing, transactions, and recovery.
- Add or update focused tests before or with changed behavior.
- During implementation, run the smallest relevant checks first.
- At the task gate, run every verification command required by the active task.
- Use emulator or physical-device checks for UI, lifecycle, security, and runtime behavior when relevant.
- Verify API 23 and API 36 when required by the active task.
- Compilation alone does not prove completion.
- Report exact commands and actual results.
- An unavailable required check is a blocker, not a pass.
- Review the intended diff and working tree before claiming completion.

## Git and Existing Changes

- Read-only Git inspection is allowed.
- Inspect `git status --short` before editing.
- Treat pre-existing modified or untracked files as user work.
- Do not overwrite, delete, reformat, stage, or incorporate unrelated changes.
- If an intended edit overlaps existing local work, inspect the diff and preserve it or stop for clarification.
- Without separate explicit approval, do not commit, push, merge, rebase, create a pull request, delete a branch, perform a destructive reset, or restore files in a way that discards changes.
- Keep Android and backend diffs separate.

## Context and Navigation Efficiency

- Do not scan or reread the entire repository by default.
- At startup, read only the implementation plan's verified status summary and the exact active task section.
- Read the immediately preceding task only when a specific output is a direct dependency.
- Load only the active task's listed files, relevant tests, and direct dependencies.
- Read architecture or contract documents only when the active task explicitly references them or an actual ambiguity requires them.
- Do not reread unchanged instruction or contract documents during the same session.
- Use targeted symbol/text navigation instead of broad repository scans.
- Graphify and code graphs are navigation aids only. Reopen actual source and tests before editing or making claims.
- Do not generate or refresh a code graph unless the user explicitly requests it.
- Prefer isolated subagents for searches, logs, and documentation work that would otherwise flood the main context.
