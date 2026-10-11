# API contract

All endpoints except `/actuator/health` require HTTP Basic credentials. JSON request/response bodies. Angular uses relative `/api` URLs through its proxy. No CORS configuration is needed for this same-origin arrangement.

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/me` | Either | Username and role |
| POST | `/api/claims` | Claimant | Submit incident; 201 or 200 for a replay |
| GET | `/api/claims` | Either | Claimant's own claims or officer's full queue |
| GET | `/api/claims/{id}` | Either | Visible claim with history |
| POST | `/api/claims/{id}/information` | Owning claimant | Respond to requested information |
| POST | `/api/officer/claims/{id}/assign` | Officer | Pick up a submitted claim |
| POST | `/api/officer/claims/{id}/request-information` | Assigned officer | Ask claimant for information |
| POST | `/api/officer/claims/{id}/decision` | Assigned officer | Settle or reject |
| GET | `/api/officer/dashboard` | Officer | Status counts, workload, exposure, settlements |

## Submission

Required header: `Idempotency-Key: <UUID>`. Generate one key for a logical submission and retain it for retries. Missing/malformed keys return 400. Angular supplies the header automatically; other clients must include it.

- First success: 201, `Idempotency-Replayed: false`.
- Same claimant/key and equivalent payload: 200, `Idempotency-Replayed: true`, original response, no extra claim/history/event.
- Same claimant/key with different payload: 409. Each claimant has an independent key namespace.
- Failed submissions roll back the receipt and claim/event changes, so a corrected request can retry the key.

A database unique constraint handles concurrent requests across instances. Decimal notation is normalised (`100` equals `100.00`); other fields are compared exactly via a fingerprint. Receipts survive restart and replay the original snapshot even if assessment has progressed. Use GET for current status/version. Receipts have no automatic expiry.

```json
{
  "type": "MOTOR",
  "market": "MY",
  "incidentDate": "2026-01-01",
  "description": "Rear bumper damaged in a collision",
  "estimatedAmount": 1200.00
}
```

Types: MOTOR / PROPERTY. Markets: MY, SG, HK, TH, ID, VN. Currency is derived by the server. Incident date cannot be in the future. Amount must be 0.01–999999999.99 with no more than two decimal places. Description is nonblank and at most 2000 characters.

The returned claim includes `id`, `version`, `claimant`, request fields, `currency`, `status`, `assignedOfficer`, `settlementAmount`, timestamps, and `history`. New claims have version 0; each mutation returns the updated version.

## Assignment / information request / information response

```json
{ "version": 0, "message": "Picked up for review" }
```

Always send the version from the latest claim response, not a hard-coded value. Messages are nonblank and at most 2000 characters.

## Decision

```json
{ "version": 3, "status": "SETTLED", "message": "Assessment approved", "settlementAmount": 1000.00 }
```

For rejection, use `status: "REJECTED"` and omit `settlementAmount` or send null. A settlement requires a positive valid amount. Amounts above the initial estimate are allowed deliberately; estimate is not a policy limit.

## Errors

| Status | Meaning |
| --- | --- |
| 400 | Malformed input, validation failure, unsupported market, invalid decision |
| 401 | Missing or incorrect credentials |
| 403 | Wrong role or not the assigned officer |
| 404 | Missing claim or inaccessible claimant-owned claim |
| 409 | Stale version, disallowed transition, or submission key reused with a different payload |

Controller/service errors have `{ "message": "..." }`. Authentication/authorization filter failures may use Spring's standard empty/error response rather than that shape; the frontend has fallbacks.

## Kafka event

Topic: `claim-events`. Key: claim UUID. JSON string value:

```json
{
  "eventId": "UUID",
  "eventType": "CLAIM_CHANGED",
  "schemaVersion": 1,
  "claimId": "UUID",
  "claimVersion": 3,
  "status": "SETTLED",
  "occurredAt": "2026-10-08T00:00:00Z"
}
```

Events cover submissions and successful changes. The event ID supports consumer deduplication. No consumer or external notification channel is implemented.

After starting Compose, inspect actual events in a separate terminal:

```sh
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic claim-events --from-beginning
```

To exercise retry behaviour: stop Kafka after successful startup, mutate a claim through the UI, then start Kafka again. The REST update should succeed while the event stays pending, and the publisher should eventually send it. This scenario is a verification instruction, not a claim that it was executed here.
