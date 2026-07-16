# Version Project Governance

## Goal

Make the repository's agent operating contract, architectural memory, feature
specifications, and validation evidence available in every clone without
weakening credential safety or workspace-scope gates.

## Requirements

1. **GOV-01 — Agent contract**: The repository SHALL track a root `AGENTS.md`
   defining source precedence, the adaptive spec-driven workflow, execution and
   verification rules, repository boundaries, credential safety, and the
   definition of done.
2. **GOV-02 — Versioned memory**: `.specs/` SHALL not be ignored and Git SHALL
   track `.specs/STATE.md` plus at least one feature `spec.md`.
3. **GOV-03 — Scope gate**: `scripts/check-scope` SHALL accept versioned
   governance documents as non-production inputs and SHALL fail when `.specs/`
   is ignored, `AGENTS.md` is untracked, `STATE.md` is untracked, or no feature
   specification is tracked.
4. **GOV-04 — Credential safety**: Tracked files under `.specs/` SHALL be
   scanned by `scripts/check-credentials` under the same credential rules as
   every other tracked file.
5. **GOV-05 — Repository guide**: `README.md` SHALL direct agents to
   `AGENTS.md`, describe `.specs/` as versioned project memory, and SHALL not
   claim that `.specs/` is ignored or untracked.

## Acceptance Criteria

- `git check-ignore --no-index .specs/STATE.md` exits non-zero.
- `git ls-files` includes `AGENTS.md`, `.specs/STATE.md`, and feature specs.
- The clean scope, README, and credential script contract suites pass.
- A tracked credential fixture inside `.specs/` makes the credential gate fail.
- Existing non-governance worktree changes are excluded from the task commit.

## Out of Scope

- Changing product architecture or feature requirements.
- Adding nested workspace-specific `AGENTS.md` files.
- Introducing a new documentation generator or lessons automation.

## Validation Invariants

- **V1 — Idempotent fixture setup**: The scope-test repository setup SHALL
  succeed whether the cloned `HEAD` already contains the current governance
  files or requires an auxiliary fixture commit.

## Backprop Log

| ID | Date | Root cause | Invariant |
| --- | --- | --- | --- |
| B1 | 2026-07-16 | Scope fixture always attempted a commit and failed under `set -e` when the governance overlay produced no staged diff. | V1 |
