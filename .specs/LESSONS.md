# LESSONS — auto-maintained by scripts/lessons.py

> Machine-owned. Do NOT hand-edit. Changes are overwritten on the next `lessons.py` write.
> Canonical state lives in `.specs/lessons.json`. Edit lessons only via the script.
> promote_threshold=2 distinct features · window_days=45 · quarantine_threshold=2

## Confirmed (load these at Specify/Design)

Corroborated across multiple features. Safe to apply as guidance.

_none_

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

### L-003 — Assert exact base, fees and total amounts in payment UI tests even when recording screenshots.
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: member-payment-ui
- evidence: docs/receivables/evidence/member-payment-ui-review.md:69 (mobile/receivables)
- last seen: 2026-09-13T19:25:13Z

### L-004 — Advance the injected clock past payment expiry without running timers to test user-action freshness.
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `mobile/receivables` · harmful: 0
- features: member-payment-ui
- evidence: docs/receivables/evidence/member-payment-ui-review.md:68 (mobile/receivables)
- last seen: 2026-09-13T19:25:13Z

## Quarantined (failed when applied — ignore)

A confirmed lesson that recurred alongside failure. Kept for the maintainer to review.

_none_
