@AGENTS.md

# CLAUDE.md

This file defines the Claude Code workflow for the POSERPNext Android repository. Permanent cross-agent rules are imported from `AGENTS.md`.

## Task-Scoped Startup

Before proposing implementation work:

1. Do not reread `AGENTS.md` or `CLAUDE.md` with tools; Claude Code already loaded them.
2. Inspect only the minimum Git evidence:

   ```bash
   git status --short
   git branch --show-current
   git log -5 --oneline
   ```

3. Read only these parts of `docs/mobile-pos/implementation-plan.md`:
   - `Verified Implementation Status`.
   - The exact active task section.
   - The immediately preceding task only when a specific output is a direct dependency.
4. Do not read the entire implementation plan by default.
5. Compare the plan with current source, relevant tests, Git history, working-tree diff, and available fresh verification.
6. Load only the active task's listed files, focused tests, and direct dependencies.
7. Read backend, authentication, recovery, or other design documents only when the active task references them or a concrete ambiguity requires them.
8. Send the required pre-implementation report and wait for explicit approval before editing implementation files.

Do not hardcode the current task in this file. Current progress belongs only in `docs/mobile-pos/implementation-plan.md`.

## Model Routing

Use one implementation writer at a time.

- `sonnet`: default implementation, focused testing, normal debugging, and task execution.
- `fable`: difficult architecture, planning, or investigation only.
- `opus`: independent deep review, difficult debugging, security review, or final audit.
- `haiku`: lightweight read-only exploration, log summarization, mechanical documentation, and progress-plan writing only.

Do not use Haiku to:

- Decide architecture or backend contracts.
- Implement production Kotlin behavior.
- Make security or recovery decisions.
- Decide independently that a task has passed.
- Perform the final code-review verdict.

The primary model must determine the verified task verdict. Delegate only the mechanical progress update to the project subagent `progress-writer`, whose model is pinned to `haiku`.

If `progress-writer` is unavailable, do not silently update task completion with another model. Report that the project subagent must be installed or reloaded.

## Progress Update Workflow

After an approved task reaches a verified milestone or completion:

1. The primary model verifies source, tests, required task gates, and intended diff.
2. The primary model decides the factual status: `Not Started`, `In Progress`, `Blocked`, or `Completed`.
3. Invoke `progress-writer` and provide:
   - Exact task name.
   - Approved scope.
   - Status verdict.
   - Files changed.
   - Exact verification commands and results.
   - Device, preview, accessibility, or runtime evidence when applicable.
   - Checks not run and why.
   - Relevant commit IDs or confirmation that changes are uncommitted.
   - Current `git status --short`.
4. `progress-writer` may edit only `docs/mobile-pos/implementation-plan.md`.
5. Reopen and review the documentation diff with the primary model.
6. Confirm that task requirements, steps, gates, and acceptance criteria were not rewritten.
7. Report completion and stop. Do not begin the next task without explicit approval.

For the current repository state, audit Task 2C, Task 2D, and Task 2E from actual evidence. Do not blindly mark Task 2E completed merely because work has reached it.

## Android Tooling

Use the installed Android CLI skill at `~/.claude/skills/android-cli` when Android inspection or device work is relevant. Read its instructions only when the active task requires Android CLI behavior.

Gradle Wrapper remains authoritative:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew lintRelease
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew connectedDebugAndroidTest
```

Use focused `--tests` filters during TDD, then run the active task's literal gate from the implementation plan.

Use repository scripts when required:

```bash
./tools/create-test-avds.sh
./tools/run-device-tests.sh api23
./tools/run-device-tests.sh api36
```

Pin the ADB serial when multiple devices may exist. Inspect command help when syntax is uncertain. Never invent Android CLI, Gradle, or ADB flags.

## Required Pre-Implementation Report

Before changing implementation code, report concisely in Bahasa Indonesia:

- Active branch.
- Working-tree status and pre-existing local changes.
- Last task verified complete.
- Current incomplete task.
- Evidence used to determine status.
- Relevant existing tests.
- Files likely to change.
- Proposed approach.
- Risks, ambiguities, blockers, or contract mismatches.
- Confirmation that no implementation file has been modified yet.

Then wait for explicit approval.

## Implementation Workflow

- Follow the active task's exact scope, dependencies, gates, and acceptance criteria.
- Use TDD where practical.
- Keep one model as the implementation writer.
- Use Haiku subagents only for lightweight supporting work.
- Preserve unrelated local modifications.
- Stop when approval, required contract data, environment values, device evidence, or another hard gate is absent.
- After finishing the approved task, update its progress through `progress-writer`, review the doc diff, and stop.

## Required Completion Report

Report in Bahasa Indonesia:

- Summary of changes.
- Files changed.
- Tests added or updated.
- Exact verification commands and actual results.
- Preview, emulator, device, screenshot, layout, or accessibility evidence when applicable.
- Checks not run and why.
- Remaining risks and limitations.
- Implementation-plan status update performed by `progress-writer`.
- Git diff summary and current short status.
- Confirmation that no commit or push was performed.

Do not claim completion from compilation alone.

## Context Control

- Use targeted search and symbol navigation.
- Do not paste stable documents or raw long logs into the main conversation.
- Delegate disposable searches, log parsing, and mechanical progress writing to isolated subagents.
- Summarize command output and retain exact artifact paths rather than carrying full logs.
- When context becomes large, compact around:
  - Active task and approved scope.
  - Current diff.
  - Files changed.
  - Test and device results.
  - Blockers and decisions.
  - Exact next action.
- Discard raw logs, completed-task details unrelated to the active task, repeated document contents, and future-task sections.
