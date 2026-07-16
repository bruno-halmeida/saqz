# Project Initialization Context

**Gathered:** 2026-07-14
**Spec:** `.specs/features/project-initialization/spec.md`
**Status:** Design approved; ready for tasks

---

## Feature Boundary

Initialize one hybrid repository with independently buildable Kotlin backend,
Angular web, and Compose Multiplatform Android/iOS shells. Enforce a
feature-oriented hexagonal backend and prove Firebase token validation through
credential-free infrastructure. Do not implement user-facing authentication,
persistence, business features, or a landing-page migration.

---

## Implementation Decisions

### Repository And Product Surfaces

- Keep the current static `landing-page/` separate and preserve GitHub Pages.
- Use a hybrid monorepo rather than forcing every toolchain through Gradle.
- Keep independent top-level `backend/`, `frontend/`, and `mobile/` workspaces.
- Give backend and mobile separate Gradle wrappers, settings, catalogs, and build logic.
- Use Angular and TypeScript for the authenticated web application.
- Use Compose Multiplatform to share Android and iOS screens and logic where
  practical.
- Develop web, Android, and iOS as parallel first-class product surfaces.
- Keep `.specs/` ignored by Git; TLC artifacts remain local by explicit user
  choice.

### Business Logic And Contracts

- Keep authoritative business rules in the Kotlin backend.
- Do not compile backend business rules to the browser.
- Angular and mobile will consume backend contracts through generated OpenAPI
  clients in a later increment.
- Do not share backend domain or persistence models with clients.

### Backend Boundaries

- Organize backend business logic by feature using hexagonal architecture.
- Use one Gradle module per implemented backend feature.
- Keep Spring, Firebase, persistence, and HTTP concerns in adapters.
- Use a small Spring Boot composition root with no business behavior.
- Create future feature modules only when they own real behavior; avoid empty
  scaffolds for groups, athletes, games, finance, and subscriptions.

### Mobile Module Strategy

- Add KMP feature modules progressively as behavior is implemented.
- Aggregate all mobile feature modules through one umbrella Compose framework
  consumed by the iOS app.
- Do not generate one iOS framework per feature because duplicated Kotlin
  dependencies can increase binary size and produce incompatible shared types.
- Keep Android and iOS application entry points separate from shared KMP code.
- Use interfaces and dependency injection for platform services; reserve
  `expect`/`actual` for small platform primitives where an interface adds no
  value.

### Authentication Depth

- Include Firebase SDK and environment wiring for backend, Angular, Android,
  and iOS.
- Include backend bearer-token verification and a minimal protected proof
  endpoint.
- Use Firebase Auth Emulator for credential-free local and CI verification.
- Exclude login, signup, password reset, Google Sign-In, logout UI, user
  synchronization, onboarding, groups, and authorization roles.

### Agent's Discretion

- Select exact mutually compatible stable Kotlin, Spring Boot, Gradle, Compose,
  Angular, Firebase, and testing-library versions during Design using official
  compatibility documentation.
- Choose internal package names and convention-plugin names while preserving
  the specified dependency rules and observable outcomes.
- Choose the exact CI workflow split and caching strategy while preserving the
  required gates and credential-free behavior.

### Declined / Undiscussed Gray Areas -> Assumptions

- npm and a committed `package-lock.json` are the default Node dependency
  strategy because no alternative package manager was requested.
- JDK 21 and Node 22 are the default toolchain baselines from the reviewed
  design; compatibility is rechecked during Design.
- All unauthorized credential failures use one public error code to avoid
  exposing verifier details.
- `GET /api/session` is the default protected proof endpoint because it is
  read-only and does not require persistence.

---

## Specific References

- ClickUp task `86ajh8u83`: Setup do monorepo (Gradle multiproject).
- ClickUp epic `86ajh0q08`: Setup & Infraestrutura.
- ClickUp tasks `86ajh10rp` and `86ajh10t3`: Firebase client setup and Spring
  Security token validation.
- Existing landing implementation: `landing-page/` and
  `.github/workflows/deploy-pages.yml`.
- Prior reviewed design:
  `docs/superpowers/specs/2026-07-14-saqz-project-initialization-design.md`.

---

## Deferred Ideas

- Email/password and Google authentication flows.
- User persistence and synchronization.
- Group onboarding, roles, invitations, and multi-group selection.
- PostgreSQL, Flyway, OpenAPI generation, design-system implementation, and
  production deployment.
