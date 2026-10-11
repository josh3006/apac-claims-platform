# Coding and documentation conventions

These are project conventions for this assessment. They support review and maintenance; a production organisation would apply its own engineering and security policies.

## Java

- Give core classes Javadoc explaining responsibility and important boundaries: HTTP mapping, domain rules, persistence, authentication, or publication.
- Document operations with non-obvious business conditions or consequences. Explain which actor may act, allowed states, and transaction semantics where relevant.
- Write inline comments for the reason behind a decision, such as currency separation, optimistic locking, ownership concealment, or outbox acknowledgement. Avoid narrating assignments or getters.
- Use meaningful local names and explicit imports. Keep individual mutations on separate lines so reviewers can follow state changes.
- Keep workflow decisions in the service. Controllers map HTTP and validate request shape; repositories persist/query data; DTOs define the external contract.
- Enforce roles at the endpoint and service boundaries. Ownership and assigned-officer checks remain per-claim rules.
- Use BigDecimal for persisted monetary values. Include currency with monetary meaning and state whether a figure is an estimate, approval, or payment.
- Do not describe application history as tamper-proof. Its protection, retention, and independent audit storage need separate implementation.
- Keep comments consistent with code and tests. Do not add claims of regulatory compliance or security guarantees that have not been verified.

## Angular

- Explain a component/service's responsibility with a short documentation comment.
- Explain asynchronous work, credentials, polling, and version handling where a Java developer may need context.
- Keep server access in the API service; screen interaction stays in the component. Backend permissions are authoritative.
- Prefer the existing template-driven forms and async/await style for this small app. Introduce extra libraries only for a concrete need.

## Tests, commits, and logs

- Test observable behaviour: access denial, valid lifecycle changes, stale updates, failure recovery, and data correctness.
- Update documentation and the AI journal when a decision or verification result changes.
- Use commit messages that describe the change and its purpose.
- Log operational identifiers and outcomes without passwords, tokens, complete request bodies, or sensitive correspondence. Follow [OWASP logging guidance](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html) when adding structured logging.

Do not add an author/date header to every file or a comment on every line. Git records authorship/history; comments should supply the context the code cannot express clearly.
