# Phase 3 Ingestion Service — correction report

## Changes in this revision
- Uses the repository's actual shared APIs: `com.logplatform.common.Streams`, `Headers`, `Hashing`, `ApiException`, and `com.logplatform.web.CorrelationIdFilter`.
- Keeps `CacheKeys` and `LogFields` local to ingestion-service.
- Adds a Redis Lua publish script (`src/main/resources/redis/atomic-publish.lua`) so dedup check, XADD, and dedup TTL assignment run in one script invocation.
- Adds a best-effort compensation path (XDEL appended entries and DEL dedup keys) if a command in the script errors.
- Adds `ingestion.require-event-id` (default false).
- Adds single-event `Idempotency-Key` support; if both header and eventId are present, they must match.
- Adds `generatedEventIds` to the response.
- Updates LogProducer unit tests to mock the atomic Redis script.
- Keeps backpressure at HTTP 503 with Retry-After: 5 through BackpressureException/RedisExceptionHandler.

## Important batch guarantee limitation
Redis Lua scripts are atomic with respect to interleaving clients, but Redis does not roll back writes already performed when a script encounters a runtime error. This implementation preflights key types and attempts compensating XDEL/DEL on a failed XADD/SET. That compensation is best-effort: catastrophic Redis failures (for example, memory exhaustion or failure during compensation) can still prevent complete rollback. Therefore this is NOT an absolute transactional all-or-none guarantee under every Redis failure mode. A stronger guarantee requires changing the stream contract (for example, one batch envelope entry plus consumer-side expansion) or a durable staging/commit protocol and corresponding Processing Service changes.

## Verification status
- Source and repository APIs inspected from the uploaded project archive.
- Maven build/tests: NOT EXECUTED in this environment (Maven executable is unavailable).
- Do not treat this ZIP as build-verified until `mvn -pl ingestion-service -am clean test` and `mvn clean verify` pass in the user's environment.

## Integration
Extract/merge this module into the existing root project. Root `pom.xml` already includes `ingestion-service` in the uploaded archive. Do not overwrite `common-core` or `common-web`.
