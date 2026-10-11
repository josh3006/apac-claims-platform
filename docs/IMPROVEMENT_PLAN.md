# Prioritised improvement plan

The brief is for an insurance claims platform. The following are proposed engineering improvements for handling sensitive financial workflows; they are not an assertion that a banking regulation applies to this assessment.

## Applied in this review

- Core Java classes and operations now document domain rules, transaction boundaries, version checks, history limitations, and Kafka retry/duplicate behaviour.
- Angular comments explain the REST boundary, screen versus server permissions, polling, and repeated-click limitations.
- Service-level role checks complement endpoint checks. Direct-service tests verify that claimant users cannot call officer operations and officer users cannot call claimant operations.
- The service's mutation code uses descriptive locals and separates state changes for easier review.
- Project coding conventions are documented in `CODING_STANDARDS.md`.

## Highest-value next work

| Priority | Current finding | Proposed improvement | Useful acceptance evidence |
| --- | --- | --- | --- |
| 1 | Shared in-memory demo accounts and local HTTP | Real identity via OIDC, TLS, least-privilege roles, external secrets; review CSRF for the chosen login model | Two distinct claimants cannot read/change each other's data; revoked/expired identities are denied |
| 2 | Schema uses Hibernate `update`; many invariants exist only in request validation | Flyway migrations, required-column/check constraints, and appropriate indexes | Existing data survives upgrade; invalid database writes are rejected; PostgreSQL integration tests pass |
| 3 | A lost submission response can lead to a duplicate claim on retry | Persist an idempotency key scoped to claimant and request payload | Same key/payload returns the original result; changed payload is rejected; concurrent retries create one claim |
| 4 | History is mutable database storage; no dedicated security audit or request tracing | Structured audit events and request IDs, restricted access/retention, separate internal notes from claimant correspondence | An action can be traced to its actor; no credentials or sensitive request bodies appear in logs |
| 5 | Current automated workflow checks use H2 and publisher mocks | PostgreSQL/Kafka integration tests and a small browser smoke suite | Real database constraints/concurrency, broker outage/recovery, and both UI roles verified |
| 6 | Publisher has one-instance assumptions, unlimited retry lifetime, and no backlog monitoring | Outbox leasing/ordering, delivery metrics, retry/dead-letter policy, event deduplication and retention | Multiple publishers do not race; replay is safe; stuck delivery raises an observable signal |
| 7 | Queue/history reads and dashboard aggregation load all records | Pagination, filtered database queries, aggregate queries and query-driven indexes | Bounded queries and predictable response time on a larger seeded data set |
| 8 | Settlement is one officer's recorded decision; no policy/approval/payment model | Coverage validation and explicit approval rules; consider a separate approver for defined amount thresholds | Unauthorised approval denied; decision, approval, and payment statuses are distinct |

The approval policy and thresholds must come from domain stakeholders. A second approver is a proposed control to evaluate, not a requirement invented for the brief. Likewise, currency-specific decimal/minor-unit rules should be agreed before expanding beyond the current two-decimal demo convention.

## Keep the assessment focused

For a small follow-up increment, prioritise idempotent submission plus database migrations and their tests. These demonstrate reliability without making Angular harder to understand. Introducing microservices would not resolve the specific gaps above.

An additional frontend fix worth scheduling is cancellation/session guarding for in-flight polling: a response that completes after sign-out should not repopulate screen state. Keep the single-component structure until growth warrants extraction.

## References

- [Spring Security method authorization](https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/method-security.html): service checks use `@EnableMethodSecurity` and `@PreAuthorize`.
- [Flyway versioned migrations](https://documentation.red-gate.com/fd/versioned-migrations-273973333.html): versioned scripts and recorded checksums support controlled schema evolution.
- [OWASP logging guidance](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html): security events need useful context while excluding or protecting sensitive values.

Prioritisation and proposed acceptance checks are findings from this code review, not requirements quoted from those references.
