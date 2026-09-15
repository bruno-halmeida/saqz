# LESSONS — auto-maintained by scripts/lessons.py

> Machine-owned. Do NOT hand-edit. Changes are overwritten on the next `lessons.py` write.
> Canonical state lives in `.specs/lessons.json`. Edit lessons only via the script.
> promote_threshold=2 distinct features · window_days=45 · quarantine_threshold=2

## Confirmed (load these at Specify/Design)

Corroborated across multiple features. Safe to apply as guidance.

### L-003 — Assert exact base, fees and total amounts in payment UI tests even when recording screenshots.
- signal: `surviving_mutant` · recurrence: 2 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: member-payment-ui, charge-approval
- evidence: docs/receivables/evidence/member-payment-ui-review.md:69 (mobile/receivables) (+1 more)
- last seen: 2026-09-13T20:39:42Z

## Candidates (under observation — do NOT load as guidance yet)

Seen once or not yet corroborated. Tracked, not trusted.

### L-001 — Correlate uncertain financial commands against pre-command instrument IDs before clearing recovery markers.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: member-payment-ui
- evidence: docs/receivables/evidence/member-payment-ui-review.md:75 (mobile/receivables)
- last seen: 2026-09-13T19:25:13Z

### L-002 — Revalidate buffered UI effects against generation, session and current permission when consuming them.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/presentation` · harmful: 0
- features: member-payment-ui
- evidence: docs/receivables/evidence/member-payment-ui-review.md:76 (mobile/presentation)
- last seen: 2026-09-13T19:25:13Z

### L-004 — Advance the injected clock past payment expiry without running timers to test user-action freshness.
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: member-payment-ui
- evidence: docs/receivables/evidence/member-payment-ui-review.md:68 (mobile/receivables)
- last seen: 2026-09-13T19:25:13Z

### L-005 — Make explicit financial recovery consult authoritative state before replaying the saved command.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: charge-approval
- evidence: docs/receivables/evidence/charge-approval-review.md CA4 recovery-first (mobile/receivables)
- last seen: 2026-09-13T20:39:42Z

### L-006 — Preserve entry-owned financial recovery markers across every allowed navigation exit.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: charge-approval
- evidence: docs/receivables/evidence/charge-approval-review.md CA4 route exit (mobile/receivables)
- last seen: 2026-09-13T20:39:42Z

### L-007 — Test each conjunctive payment permission with every other guard satisfied.
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: charge-approval
- evidence: docs/receivables/evidence/charge-approval-review.md all_terms (mobile/receivables)
- last seen: 2026-09-13T20:39:42Z

### L-008 — Exercise new authenticated financial routes through the real security chain for exact unauthorized outcomes.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: charge-approval
- evidence: docs/receivables/evidence/charge-approval-review.md CA1 security coverage (mobile/receivables)
- last seen: 2026-09-13T20:39:42Z

### L-009 — Route native and header Back through the same financial parent refresh callback.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: charge-approval
- evidence: docs/receivables/evidence/charge-approval-review.md CA6 native return (mobile/receivables)
- last seen: 2026-09-13T20:39:42Z

### L-010 — Distinguish ending a cancellation attempt from confirming cancellation when an authoritative terminal payment arrives.
- signal: `spec_precision_gap` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: charge-approval
- evidence: docs/receivables/evidence/charge-approval-review.md CA4 terminal outcomes (mobile/receivables)
- last seen: 2026-09-13T20:39:42Z

### L-011 — Use deferred native callbacks and queued dispatchers to test pending UI state and session changes before network work.
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: receivables-final
- evidence: docs/receivables/evidence/final-recurring-mobile-review.md
- last seen: 2026-09-13T23:45:00Z

### L-012 — Exercise the actual operational configuration binding to prove recovery performs only provider reads.
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `backend/receivables` · harmful: 0
- features: receivables-final
- evidence: docs/receivables/evidence/final-recurrence-operations-review.md
- last seen: 2026-09-13T23:45:00Z

### L-013 — Revalidate delegated authorization after provider reads and before claiming or submitting financial writes.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `backend/receivables` · harmful: 0
- features: receivables-final
- evidence: docs/receivables/evidence/final-wallet-management-review.md
- last seen: 2026-09-13T23:45:00Z

### L-014 — Keep entitlement lookup failures distinct from plan denial so group creation can reach its retry screen.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile` · harmful: 0
- features: trial-coupons
- evidence: AC7 / G1 (mobile)
- last seen: 2026-09-13T20:38:32Z

### L-015 — Derive monetary expectations from known fixtures before invoking the operation under test.
- signal: `spec_precision_gap` · recurrence: 1 feature(s) · scope: `tests/acceptance` · harmful: 0
- features: critical-acceptance-scenarios
- evidence: .specs/features/critical-acceptance-scenarios/validation.md F1 (tests/acceptance)
- last seen: 2026-09-14T19:29:43Z

### L-016 — Declare the navigation action that opens a screen before asserting its contents in acceptance journeys.
- signal: `spec_precision_gap` · recurrence: 1 feature(s) · scope: `tests/acceptance` · harmful: 0
- features: critical-acceptance-scenarios
- evidence: .specs/features/critical-acceptance-scenarios/validation.md F2 (tests/acceptance)
- last seen: 2026-09-14T19:29:44Z

### L-017 — Preserve failed native token revocation across session changes and restart before allowing a new authenticated device binding.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/notifications` · harmful: 0
- features: charge-notifications
- evidence: AC6 (mobile/notifications)
- last seen: 2026-09-15T15:17:46Z

### L-018 — Assert the notification destination route matches the promised user journey, including every supported charge kind.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `mobile/navigation` · harmful: 0
- features: charge-notifications
- evidence: AC5 (mobile/navigation)
- last seen: 2026-09-15T15:17:46Z

### L-019 — Exercise authenticated write endpoints through real HTTP binding and assert both accepted and rejected collection-size boundaries.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `backend/http` · harmful: 0
- features: charge-notifications
- evidence: AC1 (backend/http)
- last seen: 2026-09-15T15:17:47Z

### L-020 — Test provider-specific transient error classification and minimum retry delays instead of assuming a generic exponential schedule is sufficient.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `backend/push` · harmful: 0
- features: charge-notifications
- evidence: AC4 (backend/push)
- last seen: 2026-09-15T15:17:47Z

## Quarantined (failed when applied — ignore)

A confirmed lesson that recurred alongside failure. Kept for the maintainer to review.

_none_
