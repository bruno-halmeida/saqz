# Design

The preview is a new `application/invite/preview` use case with its own repository port. A single transactional JDBC read assembles the invite, group profile, membership count, inviter display name, regular slots, and next published future game. The HTTP adapter maps use-case outcomes to the contract-specific problem codes.

Bearer authentication becomes optional only for the exact preview path: a missing bearer continues through as anonymous, while a supplied bearer is still verified and supplies the authenticated rate-limit key. All other protected paths retain the existing filter behavior.

The anonymous limiter is process-local and bounded to 30 invalid attempts per IP per 10-minute window. `ponytail:` documents the eventual table-backed upgrade required for multiple instances.
