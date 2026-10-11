# Validation record — 2026-10-08

## 2026-10-12 Flyway and idempotency update (uncommitted)

- `mvn -s settings-local.xml -B package`: **15 tests, 0 failures, 0 errors, 0 skipped**, executable jar built. Workflow tests now run against a Flyway-created schema with Hibernate validation.
- Five submission tests cover original-response replay, amount normalisation, changed-payload conflict, claimant scoping, missing/malformed keys, failed-request rollback, and four simultaneous retries producing one claim/event.
- Three migration tests cover fresh startup/no-op restart, V1-to-V2 data preservation, and explicit baseline adoption of an actual Hibernate-generated legacy H2 schema. Claim amount, history, and pending outbox entries survive; Flyway clean is disabled.
- `scripts/npm.ps1 run test:submission`: passed. Focused frontend checks cover key reuse after a lost response, reset after success/edit/sign-out, and sending the key with authentication. These are state/HTTP-boundary checks, not browser rendering tests.
- `scripts/npm.ps1 run build`: passed with strict TypeScript/template checking.
- Live jar on port 18082 using an isolated file database under `backend/target`: full workflow smoke passed; first submission returned 201 and retry returned 200 with the same response. Stopping/restarting the process preserved the receipt and replayed the identical result; Flyway reported no pending migrations on restart.
- `docker compose config --quiet`: passed. Docker engine access remains denied, so actual PostgreSQL/Flyway/Kafka integration is unverified.
- No live user database was modified, and no Git commit or push was performed.

## 2026-10-12 review update

- User reported that the application works. This is separate from the original environment-specific checks below.
- `mvn -s settings-local.xml -B test` on Java 21: **7 tests, 0 failures, 0 errors, 0 skipped**. Two new tests exercise the service bean directly to verify role denial without relying on controller filters; the existing workflow and publisher tests still pass.
- `scripts/npm.ps1 run build` on Node 22: passed with strict TypeScript/template checking. No UI flow was changed in this review.
- `git diff --check`: passed.
- The new documentation and service authorization checks do not establish production or regulatory compliance. PostgreSQL/Kafka/browser verification remains separate from these checks.

## Passed

- `mvn -s settings-local.xml -B package` using installed Java 21.0.10: application compiled, all three workflow tests passed, executable jar produced.
- After adding publisher tests: `mvn -s settings-local.xml -B test`: **5 tests, 0 failures, 0 errors, 0 skipped**.
- Workflow integration tests use Spring Boot, Spring Security, MockMvc, and H2: real Basic authentication, role/ownership checks, input validation, information exchange, settlement/rejection, stale versions, history, outbox count, and currency-separated dashboard.
- Publisher unit tests mock the Kafka client: broker acknowledgement marks publication, failure keeps the event pending, payload includes IDs/version and excludes personal free text. This is not a live broker test.
- Angular production compilation using Node 22.13.1: strict TypeScript/template checking passed. Initial production bundle: approximately 255 kB raw / 72 kB estimated transfer.
- Clean lockfile install through `scripts/npm.ps1 ci --no-audit --no-fund`, followed by `scripts/npm.ps1 run build`: passed with Node 22, confirming the Windows helper and reproducible dependency install. The first clean-install attempt hit a Windows file lock while the dev server was running; stopping the temporary server resolved it.
- Live executable jar on port 18080 with in-memory H2: `scripts/smoke.ps1 -BaseUrl http://localhost:18080` completed submission → pickup → request information → response → settlement. Verified five history entries and dashboard settlement total.
- Angular development server on port 14200 served the HTML and proxied authenticated `/api/me` to the live backend on 18080.
- `docker compose config --quiet`: passed.

## Unverified

- Container image builds and full Compose startup: Docker engine access denied (`permission denied ... dockerDesktopLinuxEngine`).
- PostgreSQL-specific schema/persistence and real Kafka publication/recovery: require a Compose run.
- Browser interactions, responsive rendering, and accessibility: Browser runtime discovery returned no connected browsers. Build and HTTP proxy checks do not prove interactive UI behaviour.

## Corrections from failed checks

Initial checks caught a JPA managed-instance/version issue, missing BCrypt encoding prefixes, and an Angular bootstrap provider configuration mismatch. Corrected code was checked again successfully. See the AI journal.

The development machines' default Java 8 and Node 19 were not upgraded globally. Java 21 was selected for Maven; Angular compilation used an ignored project-local Node 22 executable. The Windows npm helper explicitly selects that binary because `npm.cmd` can prefer its adjacent old Node binary.
