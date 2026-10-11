# Database migrations and existing data

Flyway now owns schema creation/evolution. Hibernate uses `ddl-auto: validate`. Migration checksums are checked at startup; add a new migration rather than editing an already-applied script.

| Version | Purpose |
| --- | --- |
| V1 | Original claims, history, and outbox schema |
| V2 | Durable submission receipts, unique claimant/key constraint, foreign key to claims, and query indexes |

SQL lives under `backend/src/main/resources/db/migration`. Scripts target H2 and PostgreSQL; MSSQL/Oracle support is not claimed. Automated checks ran on H2; actual PostgreSQL startup requires the Docker environment.

## Fresh database

Start normally. Flyway executes V1 then V2 before Hibernate validates. No baseline flag or database deletion is needed. `flyway_schema_history` records versions/checksums.

## Existing database from the earlier application

The earlier Hibernate-managed database has no Flyway history. Flyway deliberately refuses a non-empty unversioned schema by default. Do not delete the database or Docker volumes to bypass this.

1. Stop application writes and back up the database. For H2, stop the process before copying `backend/data/claims.mv.db`. For PostgreSQL, use your normal logical backup procedure and verify restore on a copy.
2. Review the schema against the original claim/history/outbox model represented by V1. A baseline declares that V1 already exists; it does not create, validate, or repair missing V1 objects. Hibernate validation after migration does not replace this review.
3. Test on a restored copy. Only for this reviewed legacy schema, enable `FLYWAY_BASELINE_ON_MIGRATE=true` for one startup. Baseline version is **1**. Flyway records V1 as the baseline and executes V2 without recreating the existing tables.
4. Verify existing claims/history, pending events, and new submissions. Reset the flag to false and restart normally.

For local PowerShell, from `backend` after backup/review:

```powershell
$env:FLYWAY_BASELINE_ON_MIGRATE = 'true'
mvn -s settings-local.xml spring-boot:run
# Stop after verification, then remove the one-time opt-in:
Remove-Item Env:FLYWAY_BASELINE_ON_MIGRATE
mvn -s settings-local.xml spring-boot:run
```

For Compose, set `FLYWAY_BASELINE_ON_MIGRATE=true` in the ignored `.env` for adoption, then restore it to `false` and recreate the backend with `docker compose up -d backend`. Compose forwards the flag explicitly. Do not enable baseline for an unknown schema or the wrong database.

The implementation did not modify your live database. Tests use isolated databases, including an actual Hibernate-generated legacy schema, and verify preservation of amounts, history, and pending events.

## Limits

- Flyway `clean` is disabled; automatic baselining defaults to false.
- V2 adds objects/indexes and does not remove or rewrite claims.
- Old claims predate idempotency receipts and cannot be retrospectively deduplicated by a new key.
- Receipt JSON contains claimant-visible data; protect it like the claim tables. Receipts have no automatic expiry in this demo. A future retention policy must define the deduplication window and privacy rules.

Reference: [Spring Boot database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html).
