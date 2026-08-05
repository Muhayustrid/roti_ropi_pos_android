# Task 7 Customer Search Hotfix Design

## Scope

Fix post-merge Task 7 audit findings B1-B4 and I1-I2 only. Preserve the
versioned backend contract, memory-only customer state, 300 ms debounce,
20-record page size, 100-record cap, API 23 support, and existing touch and
adaptive UI coverage. Do not change the backend or implement Task 8 or Task 9.

## Serialized State Authority

`CustomerSearchViewModel` uses one private JVM monitor as the sole authority
for identity, generation, request ownership, pagination, query lifecycle, and
UI-state mutation. Public events and network completions enter the same monitor.
Network calls execute outside it.

A completion does not publish in a single monitor acquisition. It performs four
distinct steps:

1. a synchronized candidate check (`isCompletionCandidate`) that verifies the
   authority is still current and owns the active request;
2. a deterministic test checkpoint invoked outside the monitor, so latch-based
   tests can pause only genuinely relevant completions;
3. a mandatory revalidation under the same monitor immediately before
   publication (generation, identity, query, and active-request ownership are
   re-checked atomically with the state write);
4. state publication inside that protected critical section.

Stale-publication safety does not come from the pre-check: it depends on the
mandatory revalidation under the same mutation authority immediately before the
state write, because clear, logout, profile changes, cashier changes, and newer
retry requests can invalidate authority while the checkpoint is paused. The
pre-check exists only to keep the test checkpoint deterministic and cheap.

Request cancellation remains best-effort. Generation, identity, query, offset,
and request ID checks prevent non-cooperative old completions from publishing.
Tests use deterministic latches at the completion boundary to prove that clear,
logout, profile changes, cashier changes, and newer retry requests win every
interleaving. Concurrency tests use graceful interrupt-free executor shutdown
with a bounded termination wait so no interrupted task can leak an exception
into later tests.

Logout exposes separate customer invalidation/cancellation and UI-clear steps.
Cleanup order is customer authority invalidation, active request cancellation,
customer UI clear, repository/profile clear, then authentication clear.

## Query Lifecycle

Track normalized queries in three phases: scheduled debounce, active request,
and completed successful initial query. Identical input in any active phase is
a strict no-op, including blank and empty-result searches. A failed normalized
query is also a no-op until explicit Retry. A genuine normalized query change
invalidates obsolete work and may later return to a prior query as a new search.

Opening the production customer selector sends the current blank query through
the same debounced event path. The current query is read under the same monitor
that owns identity, so a concurrent profile or cashier rebinding can never pair
a previous identity's query with the new profile; the rebind either precedes
the selector-open and resets the query to blank, or follows it and invalidates
the scheduled blank search. Recomposition and reopening after a completed
search rely on the same no-op rules instead of separate UI flags.

## Selection Rules

A result row selects `CustomerSelection.WalkIn` only when its customer ID equals
the bound profile default customer ID and `is_default_walk_in` is true. Every
other row selects `CustomerSelection.Registered`, including incorrectly marked
rows. Walk-in selection preserves optional display-name behavior. Registered
selection clears the walk-in display name by replacing the selection value.

## Pagination Validation

Validate every consumed success page before publishing records:

- `start >= 0`
- `limit > 0`
- page requests do not repeat or regress from the requested offset
- `has_more=true` metadata produces a strictly greater next offset

Invalid initial metadata publishes a recoverable protocol error with no
records. Invalid page metadata retains prior records, publishes a page protocol
error, and retains the exact failed request authority for explicit Retry. It
never exposes Load more until valid advancing metadata arrives.

## Keyboard Accessibility

Customer sheet controls participate in explicit logical focus order: search,
customer rows, Retry or Load more when present, and Done. Official
instrumentation uses external-key events such as Tab, D-pad, and Enter to
traverse and activate controls; direct tagged clicks are not accepted as proof
for the keyboard journey. Existing touch, compact, landscape, and font-scale
tests remain.

## Verification

Follow TDD in audit order. Use coroutine test scheduling and latches/barriers,
not arbitrary sleeps, as concurrency authority. Run focused customer/logout
unit tests, instrumentation assembly, Task 7 instrumentation through the
supported API 23 and API 36 harness, then every full Gradle gate command
serially and `git diff --check`.

Final independent review is intentionally outside this implementation session.
After source and verification stop changing, the project owner will start a
separate read-only main-agent session. No commit, push, or PR may occur before
that reviewer returns `APPROVE — no blocking findings`.
