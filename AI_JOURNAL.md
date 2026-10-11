# AI working journal

This is a running record of the actual conversation and implementation. AI implementation choices below have not yet been personally reviewed or approved by the candidate. Add your own challenges, corrections, and reasoning as you review. Do not present generated choices as decisions you have already defended.

## 2026-10-08 — brief and scope

- User asked AI to read `V2_FullStack_candidate_assessment_brief.docx`. AI extracted the document and identified backend-first prioritisation, Angular, Java/Spring Boot or .NET, REST/Kafka decisions, local startup, commit history, and an AI journal.
- User asked whether code was necessary and suggested code → GitHub → Docker. AI clarified that executable code and Git history are required; GitHub and Docker are implementation choices.
- User accepted proceeding and explicitly required Spring Boot/Java with Angular kept as simple and documented as possible.

## AI implementation choices — pending candidate review

- One Spring Boot service, Java 21, Maven, JPA. Avoided microservices for a short assessment; used immutable record DTOs with conventional controller/service/repository layers.
- Angular: one standalone component, a typed HTTP service, template-driven forms, ordinary component properties, and 10-second polling. No router, NgRx, UI kit, or custom RxJS pipelines.
- H2 for an easy local run; PostgreSQL and Kafka in Compose. Six demo markets and their currencies are explicit assumptions.
- HTTP Basic demo accounts with backend role/ownership enforcement. Credentials remain in browser memory. Production identity is deferred.
- Optimistic locking with a request version; audit history with each mutation; currency-separated exposure; a transactional outbox with retryable Kafka publication. Kafka payload excludes claimant descriptions and messages.
- AI challenged using the machine's default Java 8 and Node 19 for current tooling; selected an installed Java 21 and a project-local Node 22 binary instead of modifying system defaults.
- Docker engine access was denied in this environment. Compose execution must be reported as unverified unless a later successful run is recorded.

## Candidate review log — fill in while reviewing

| What I asked/challenged | What I accepted/overrode | Why | Evidence/check |
| --- | --- | --- | --- |
| Pending personal review | | | |

## Validation record

Actual command results are recorded in `docs/VALIDATION.md` after execution. Do not claim a Docker or Kafka integration run from an H2-only test.

## Corrections driven by checks

- Initial workflow tests caught an incorrect returned version caused by using the original entity after JPA merge with a manually assigned UUID. AI changed the version to nullable `Long` for new-entity detection and used the managed object returned by `saveAndFlush`.
- The real Basic-auth test caught missing `{bcrypt}` prefixes for Spring's delegating password encoder. AI corrected the stored encoded password format.
- Angular compilation caught an incorrect bootstrap configuration shape. AI passed `{ providers: [...] }` rather than a provider array.
- No browser was connected (runtime discovery returned no available browsers); browser interaction remains unverified. AI did not claim UI testing from compilation.

## 2026-10-12 - documentation and engineering review

- User reported that the application works and requested standard code comments plus suggestions for a financial-system context.
- AI added class/operation documentation and comments explaining business constraints, transactions, concurrency, money, security boundaries, and outbox delivery. It made service mutation code easier to review without changing the claim contract.
- AI added method-level role checks and direct-service authorization tests so future non-controller callers also receive role enforcement.
- AI recorded coding conventions and a prioritised improvement plan. Larger changes (identity, migrations, idempotency, audit infrastructure, integrations, approvals) are recommendations, not implemented controls.
- The original domain remains insurance claims. No banking-compliance claim or invented mandatory approval threshold was added.
- User's successful application run is user-reported; earlier Docker/browser limitations remain historical records of the AI environment. New verification results are recorded in `docs/VALIDATION.md`.
- Verification after the update: all seven backend tests passed; Angular production compilation passed; `git diff --check` passed.

## 2026-10-12 - Flyway and idempotent submission (local only)

- User initially selected Flyway and idempotent claim submission for local changes only. After reviewing the result, the user explicitly authorised committing and pushing the changes.
- AI added V1/V2 SQL and changed Hibernate to validate. Legacy adoption requires backup/review and a one-time explicit baseline at version 1. The live database was not modified.
- AI added durable receipts per claimant/UUID key, request fingerprints, original-response replay, changed-payload conflicts, and database uniqueness for concurrent retries.
- A separate transactional worker lets a duplicate-key transaction finish rolling back before a new transaction reads the receipt. Claim, history, outbox, and receipt commit atomically.
- Angular reuses keys after lost responses and resets them on success, edits, or sign-out; cross-reload recovery is explicitly deferred.
- Added API/concurrency/rollback/migration-preservation tests and focused frontend retry/header tests. Actual outcomes are recorded in validation documentation.
- Final checks: 15 backend tests passed, frontend retry/header tests and production build passed, and live file-database replay remained correct after a process restart. Compose configuration passed; Docker engine access is still denied.
