# Saqz Agent Instructions

These instructions apply to the entire repository. A nested `AGENTS.md` may add
workspace-specific rules, but it must not weaken this contract.

## Sources of Truth

Use this precedence when instructions conflict:

1. The current user request and platform-level instructions.
2. The closest applicable `AGENTS.md`.
3. The active feature's confirmed `context.md`, `spec.md`, `design.md`, and
   `tasks.md`.
4. Active decisions in `.specs/STATE.md`.
5. `README.md`, repository scripts, tests, and established code patterns.

Stop and ask for direction when two higher-precedence sources disagree. Current
code is evidence of existing behavior, not permission to override an accepted
requirement.

## Before Changing Files

- Prefix shell commands with `rtk`, as required by the local RTK instructions.
- Inspect `git status` and preserve unrelated or pre-existing worktree changes.
- Read only the active feature documents. Do not load multiple feature specs or
  architecture documents at once.
- Check the codebase first, then project documentation, before consulting
  external documentation.
- State assumptions, files to touch, and deterministic success criteria before
  implementation.

## Spec-Driven Workflow

Use the adaptive flow `Specify -> Design -> Tasks -> Execute -> Verify`.

- Every change needs a clear expected outcome. A tiny change may use an inline
  one-line spec; substantive product behavior belongs in
  `.specs/features/<feature>/spec.md` with traceable acceptance criteria.
- Create `context.md` when requirements contain unresolved behavior or implicit
  state, persistence, external calls, authorization, payments, concurrency, or
  state transitions.
- Create `design.md` and `tasks.md` only for large or complex work. If an inline
  execution plan grows beyond five steps or gains complex dependencies, stop
  and create `tasks.md`.
- Keep project-wide architectural decisions and the current handoff in
  `.specs/STATE.md`. Do not rewrite earlier decisions; supersede them explicitly.
- Keep `.specs/` versioned and free of credentials. Validation evidence belongs
  beside its feature in `validation.md`.

## Implementation Contract

- Work on one atomic task at a time and touch only files required by that task.
- Derive tests from acceptance criteria and assert specified outcomes, not
  implementation details.
- Never weaken, skip, or delete a test to make a gate pass.
- Run the task's relevant gate before completion. A non-zero exit means the task
  is not done.
- Commit each completed task atomically with a Conventional Commit message. Do
  not include unrelated worktree changes.
- After the final task, run a fresh spec-anchored verification and record the
  evidence in `validation.md`. The verifier must be independent of the author
  when agent delegation is available; otherwise perform an explicit fresh-eyes
  standalone pass.
- For a formal plan larger than roughly eight tasks, offer sequential
  task-budgeted agent batches and wait for user confirmation before delegating.

## Repository Boundaries

- `backend/` is an independent Kotlin/Spring workspace. Backend feature
  dependencies flow `bootstrap -> features:<feature> -> shared-kernel`.
- `mobile/` is an independent Compose Multiplatform workspace. Shared features
  are aggregated by `mobile/compose-app`; iOS consumes only its umbrella
  framework.
- `landing-page/` is independent static public content and is not a dependency
  of backend or mobile builds.
- The repository root owns orchestration only. Do not add a root Gradle build or
  couple backend and mobile build logic or artifacts.
- Do not introduce a browser product workspace without a new spec and an active
  architecture decision.
- Keep authoritative business rules in the backend. Client code must not import
  backend domain or application internals.

## Verification

Use the narrowest relevant gate while iterating, then the required aggregate for
the task:

- `rtk scripts/check-credentials` for tracked-file secret safety.
- `rtk scripts/check-scope` for workspace and product-scope invariants.
- `rtk scripts/test-scripts` for repository script contracts.
- `rtk scripts/check-gradle` for backend and Android/KMP checks.
- `rtk scripts/check-ios` for the complete local iOS contract on macOS.
- `rtk scripts/check-landing` for the preserved static landing page.
- `rtk scripts/check-all` for the complete local gate.

Never commit production Firebase configuration, service-account material,
signing identities, database credentials, bearer tokens, or non-example
environment files. If a credential-like value is needed in a test, use an
obviously fake fixture that passes `scripts/check-credentials`.

## Definition of Done

A task is complete only when its acceptance criteria have concrete evidence,
relevant tests and gates pass, no unrelated files are included, documentation
matches the delivered behavior, and final feature validation is recorded.
