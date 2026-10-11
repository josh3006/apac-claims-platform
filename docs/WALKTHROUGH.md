# Panel walkthrough preparation

## 15–20 minute presentation

- 2 minutes: the problem, time constraints, and chosen core journey. Explain backend-first scope and user-requested Angular simplicity.
- 4 minutes: the architecture diagram, claim lifecycle, security boundaries, transaction/version checks, and REST/Kafka split.
- 6 minutes: submit → pick up → request information → respond → settle. Use two browser windows and refresh when needed. Show history and dashboard.
- 3 minutes: show `ClaimService`, one request DTO, the workflow tests, and the small Angular service/template. Explain exactly what you understand.
- 2 minutes: show actual validation results and the AI journal. Clearly identify unfinished checks and demo shortcuts.

## Questions to be able to answer

- Why one service? Cohesive transactional domain and assessment time; independently deployed services are justified only by separate scale/ownership requirements.
- Why Kafka if REST works? REST serves immediate user actions; Kafka decouples downstream work. The outbox avoids a database/Kafka dual-write gap.
- What if Kafka is down? Claim commits succeed; events wait and retry. Backlog monitoring is future work.
- Can Kafka events duplicate? Yes. Broker acknowledgement followed by a crash before marking the row can replay the event. Deduplicate event IDs.
- What if two officers pick up a claim? The version checks and JPA optimistic locking reject one competing/stale update with 409.
- Is the status badge security? No. Backend role and ownership checks enforce access.
- Is exposure a financial reserve? No. It is an explicitly labelled sum of submitted estimates, separated by currency.
- Why a single Angular component? Small scope and easier personal comprehension. Extract components once feature growth justifies it.
- Why polling? Easy to inspect and adequate for a prototype. SSE would suit lower-latency updates without connecting browsers to Kafka.
- What happens if submission times out? Retry with the same key and unchanged payload. A durable receipt returns the original result; a changed payload gets 409. Angular retains keys in memory but does not support recovery across reloads.
- How do schema changes roll out? Flyway applies versioned SQL; Hibernate validates. Existing schemas need backup/review and explicit baseline adoption.

## More time: prioritised work

Start with Docker/PostgreSQL/Kafka verification if pending. Then production identity, policy checks, receipt retention/reload recovery, database constraints, and attachments. Add notifications with deduplication, outbox monitoring, pagination, SLA metrics, and browser tests.

## Your own review

Read each Java class and trace one complete Angular request. Run the demo personally. Add your actual challenges/overrides to the AI journal. Do not claim you reviewed code just because AI wrote explanatory documentation.

Useful panel questions: How are claims services divided today? Who owns API contracts? What events exist? How are engineers expected to review AI-generated code?
