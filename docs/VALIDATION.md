# Validation record — 2026-10-08

## Passed

- `mvn -s settings-local.xml -B package` using installed Java 21.0.10: application compiled, all three workflow tests passed, executable jar produced.
- After adding publisher tests: `mvn -s settings-local.xml -B test`: **5 tests, 0 failures, 0 errors, 0 skipped**.
- Workflow integration tests use Spring Boot, Spring Security, MockMvc, and H2: real Basic authentication, role/ownership checks, input validation, information exchange, settlement/rejection, stale versions, history, outbox count, and currency-separated dashboard.
- Publisher unit tests mock the Kafka client: broker acknowledgement marks publication, failure keeps the event pending, payload includes IDs/version and excludes personal free text. This is not a live broker test.
- Angular production compilation using Node 22.13.1: strict TypeScript/template checking passed. Initial production bundle: approximately 255 kB raw / 72 kB estimated transfer.
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
