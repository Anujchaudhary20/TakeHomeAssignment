# Multi-Tenant Accounting Ledger Service

A high-integrity, multi-tenant double-entry accounting ledger backend service built with **Java 17+**, **Spring Boot 3.3**, **Spring Data JPA / Hibernate 6**, **PostgreSQL**, and **Flyway**.

---

## 1. Quick Start & How to Run

### Prerequisites
* Java 17+ (Java 17, 21, or 23)
* Docker & Docker Compose (optional, for containerized run)
* Or local Maven / Gradle (wrapper included)

### Option A: Running with Docker Compose (Recommended)
Starts PostgreSQL and the Ledger Service with a single command:
```bash
docker compose up --build
```
The service will be live on `http://localhost:8080`.

### Option B: Running Locally with Maven Wrapper (Zero Configuration)
If running locally without Docker:
```bash
./mvnw clean spring-boot:run
```
*(Runs out-of-the-box in 3 seconds with zero external database setup required, using an in-memory PostgreSQL-compatible database with automatic Flyway migrations. If you have an external PostgreSQL instance running, simply provide `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ledger_db ./mvnw spring-boot:run`)*.

### How to Run the Tests
To execute the automated integration and unit test suite:
```bash
./mvnw clean test
```
All tests run against an in-memory database in PostgreSQL compatibility mode with Flyway schema migration validation enabled (`ddl-auto: validate`).

---

## 2. Architectural Decisions & Rationale

### 2.1 Stopping Duplicate Entries (Idempotency & Concurrency)
**Challenge**: When network timeouts occur, clients retry. If two identical requests arrive at the exact same millisecond across multiple threads or service instances, a naive `"check-then-insert"` produces duplicate journal entries, corrupting the tenant's financial records.

**Design & Solution**:
1. **Database-Enforced Mutual Exclusion**: We created an `idempotency_record` table with a strict composite unique constraint: `UNIQUE (tenant_id, idempotency_key)`.
2. **Atomic Reservation (`REQUIRES_NEW`)**: Before processing a request, `IdempotencyRecordReservationService` attempts to insert an `IN_PROGRESS` record with the SHA-256 hash of the request body.
3. **Trace of Concurrent Arrival at the Same Millisecond**:
   * **Thread 1** wins the insert and acquires the lock. It validates the entry, executes the domain logic, persists the `JournalEntry`, updates the idempotency record with `status = COMPLETED`, cached HTTP status (`201`), and serialized JSON response body, then commits.
   * **Thread 2** hits a `DataIntegrityViolationException` on the unique constraint `(tenant_id, idempotency_key)`.
   * **Thread 2** catches the exception without failing the request. It verifies that the incoming request's SHA-256 payload hash matches the existing record (rejecting key hijacking with `422 Unprocessable Entity`).
   * **Thread 2** polls the database with exponential backoff / short intervals (100ms, up to 3s). As soon as Thread 1 marks `COMPLETED`, Thread 2 deserializes and returns the cached response.
   * **Result**: Exactly **one** journal entry is created in the database, zero duplicate entries, and both callers receive successful responses.

### 2.2 Tenant Separation: Making it Safe by Default
**Requirement**: *"Make it so that a developer who forgets about tenants still writes safe code."*

**Design & Solution**:
1. **Hibernate 6 Native `@TenantId` Partitioning**: We placed `@TenantId` directly on the `tenantId` field of `JournalEntry` and `JournalEntryLine`.
2. **Automatic Query Rewriting**: In Hibernate 6, `@TenantId` integrates with `CurrentTenantIdentifierResolver`. Hibernate automatically intercepts the query AST and appends `WHERE tenant_id = :currentTenant` to **every single generated SQL query** (`findById`, `findAll`, custom queries, updates, and deletes).
3. **Safe by Default**: If a junior developer writes `journalEntryRepository.findById(id)` without any tenant condition, Hibernate automatically restricts the search to the active tenant. A cross-tenant lookup returns `Optional.empty()` $\to$ `404 Not Found`.
4. **Information Leakage Prevention**: We strictly return `404 Not Found` rather than `403 Forbidden` for cross-tenant ID accesses. Returning `403` would confirm to Tenant B that the resource ID exists in another tenant's system.
5. **How Someone Could Still Defeat It** *(Interview Question)*:
   * **Raw Native SQL**: If a developer executes an unescaped `@Query(value = "SELECT * FROM journal_entry WHERE id = :id", nativeQuery = true)`, Hibernate does not parse the SQL AST, bypassing `@TenantId`. *(Defense: PostgreSQL Row-Level Security / RLS policies at the DB role level).*
   * **Unpropagated Async Context**: If code delegates work to `@Async`, `CompletableFuture.supplyAsync`, or a background worker without copying `TenantContext` across threads, `TenantContext` will be unassigned.

### 2.3 Monetary Type Representation
**Choice**: Java `BigDecimal` (stored with scale 4 / 2 and `RoundingMode.HALF_UP`) and PostgreSQL `NUMERIC(19, 4)`.

**Rationale**:
* `float` and `double` use IEEE 754 binary floating-point representation, which cannot accurately represent decimal fractions like `0.10` or `0.01` ($0.1 + 0.2 = 0.30000000000000004$). In accounting, accumulated rounding errors cause balancing violations and audit failures.
* `BigDecimal` provides arbitrary-precision arithmetic with deterministic rounding modes.
* Storing `NUMERIC(19, 4)` in PostgreSQL supports fractional currency units, micro-transactions, and standard financial precision up to 4 decimal places while preventing overflow up to 1 quadrillion units.

### 2.4 Double-Entry Balancing & Validation Placement
**Requirement**: *"Reject entries that do not balance. Think about where in your application this check belongs."*

**Decision**: The balance rule (`Total Debits == Total Credits`, $\ge 2$ lines, positive amounts) is implemented as a **Domain Invariant** inside the `JournalEntry` aggregate root (`entry.validateBalancing()`) and verified in `JournalEntryService`.

**Rationale**:
* In Domain-Driven Design (DDD), `JournalEntry` is an aggregate root. An unbalanced journal entry represents an invalid domain state.
* If validation only lived in the REST Controller / DTO validator, any entry created through alternative channels (webhook ingestion, background processing, data migration scripts, internal services) could bypass the check.
* Placing the invariant on the domain aggregate guarantees that no code path can ever construct or persist an unbalanced entry to the database.

### 2.5 Reversal Design & The "Double Reversal" Rule
**Decision**: An entry can only be reversed **once**. A second reversal attempt is rejected with `409 Conflict`.

**Rationale**:
* Reversing an entry creates an offsetting entry with debits and credits swapped, bringing the net balance back to zero.
* If a tenant were permitted to reverse an entry twice, the second reversal would re-apply the original transaction's financial effect in reverse, creating an invalid phantom balance.
* Reversals preserve absolute immutability: the original entry is never deleted or altered. The relationship is recorded bidirectionally (`reversal_of_entry_id` on the reversal entry, and `reversed_by_entry_id` on the original).

### 2.6 Webhook Ingestion & Resiliency
**The Four Situations Handled**:
1. **Raw-Byte HMAC Verification**: Webhook payloads are verified using a custom servlet filter (`HmacSignatureFilter`) and `CachedBodyHttpServletRequest`. The HMAC-SHA256 signature is calculated against the **raw byte stream** before Jackson parses the body. Constant-time comparison (`MessageDigest.isEqual`) is used to prevent timing attacks. Invalid signatures return `401 Unauthorized` and write nothing.
2. **Deduplication**: The `webhook_inbox` table enforces `UNIQUE (event_id)`. If the provider delivers `evt_9f2b7c41` a second time, the service detects the existing processed record and returns `200 OK` with status `ALREADY_PROCESSED` without creating duplicate journal entries.
3. **HTTP Status Code Timing**: We return `200 OK` **strictly after** the database transaction has committed.
   * *Why?* If we replied with 200 before persisting to the DB, and the server subsequently crashed or DB connection dropped, the provider would believe the notification succeeded and would never retry. Returning 200 after commit guarantees that any failure returns `500 Internal Server Error`, instructing the provider to retry via backoff.
4. **Failure Recovery (Inbox Pattern)**: Webhook events are logged to `webhook_inbox` (`RECEIVED`, `PROCESSED`, `FAILED`). If transient DB degradation occurs, the event payload is preserved, enabling automated retry jobs or administrative replay.

---

## 3. Assumptions Made

1. **Account Codes**: As per guidelines, account codes (e.g., `1000-BANK`, `4000-SALES`) are treated as plain strings; no chart of accounts or pre-registration is enforced.
2. **Webhook Accounts**: Settled payment webhooks debit default bank account `1000-BANK` and credit sales revenue account `4000-SALES`.
3. **Currency**: Single currency (`INR`) assumed per tenant; no real-time foreign exchange conversions required.
4. **Idempotency Lifetime**: Idempotency records are persisted permanently in PostgreSQL to prevent duplicate postings indefinitely. In high-volume production, records could have a TTL (e.g., 24–48 hours).

---

## 4. Answering Section 9 & 10 Interview Questions

### 1. Walk us through what happens when a journal entry is created, from controller down to DB.
* `POST /api/v1/journal-entries` arrives at Tomcat.
* `TenantFilter` extracts `X-Tenant-Id` header and sets `TenantContext`.
* `JournalEntryController` receives the DTO and invokes `idempotencyService.executeIdempotent()`.
* `IdempotencyService` inserts an `IN_PROGRESS` record with SHA-256 payload hash in a new transaction.
* `JournalEntryService.createJournalEntry()` maps DTO to `JournalEntry` domain aggregate, adds lines, and runs `entry.validateBalancing()`.
* Hibernate generates insert SQL. Because `@TenantId` is enabled, Hibernate injects `tenant_id`.
* The transaction commits. The idempotency record is marked `COMPLETED` with cached response.
* Controller returns `201 Created`. `TenantFilter` clears `TenantContext` in a `finally` block.

### 2. Show us where you check that debits equal credits, and tell us why it lives there.
* Located in `com.ledger.ledger.model.JournalEntry#validateBalancing()`.
* It lives in the domain aggregate root because double-entry balance is an inviolable business invariant. If it lived only in the controller/validator, webhooks or background batch processes could bypass it.

### 3. Two identical requests arrive at the same millisecond. Trace what happens.
* Both execute `IdempotencyRecordReservationService.reserve()`.
* Thread 1 wins the DB insert on `UNIQUE (tenant_id, idempotency_key)`.
* Thread 2 throws `DataIntegrityViolationException`. Thread 2 catches it, checks payload hash integrity, and polls for Thread 1's completion.
* Thread 1 commits the journal entry and caches the response.
* Thread 2 reads the cached response and returns it without inserting a second entry.

### 4. Show us how tenant separation works, and tell us how someone could still get around it.
* Uses Hibernate 6 native `@TenantId` with `HibernateTenantIdentifierResolver` reading from `TenantContext`. Every JPA query automatically appends `WHERE tenant_id = ?`.
* *Bypasses*: (1) Developer writing unescaped `nativeQuery = true` in Spring Data JPA; (2) Background thread or async task executed without tenant context propagation.

### 5. Why did you choose that type for money?
* `BigDecimal` (scale 4, `RoundingMode.HALF_UP`) and PostgreSQL `NUMERIC(19, 4)`: avoids IEEE 754 floating-point inaccuracies while supporting sub-cent precision and preventing integer overflow.

### 6. Which of your tests would catch a real bug?
* `JournalEntryConcurrencyAndIdempotencyTest.testConcurrentIdenticalRequestsCreateOnlyOneEntry()`: 10 parallel threads firing simultaneously using `CountDownLatch`. Catches race conditions and ensures that simultaneous requests do not create duplicate ledger records.
* `TenantIsolationSecurityTest`: Catches unauthorized data leaks across tenants and ensures cross-tenant queries return 404 instead of leaking existence.

### 7. What is the weakest part of this submission?
* In-flight polling in the idempotency layer: When request 2 arrives while request 1 is in-flight, thread 2 sleeps for 100ms intervals. In ultra-high throughput environments, this ties up a Tomcat worker thread. A distributed pub/sub notification (Redis keyspace notifications or reactive async callbacks) would be more resource-efficient.

### 8. If this had to handle 100 times the traffic, what would break first?
* **Database Connection Pool Exhaustion (HikariCP)**: Polling in-flight idempotency locks and synchronous DB writes under heavy load would exhaust the connection pool.
* **Solution**: Migrate idempotency state checks to a distributed cache (Redis) with fast TTLs, and adopt the **Transactional Outbox Pattern** with Kafka for asynchronous ledger ingestion and balance aggregation.

---

## 5. Optional Kafka & Reporting (Transactional Outbox Pattern)

**Problem**: If you publish directly to Kafka inside a Spring `@Transactional` method, and the database transaction subsequent to the message publish rolls back, the Kafka message has already been broadcast to downstream consumers. Downstream systems now process phantom transactions.

**Solution**: The **Transactional Outbox Pattern**:
1. Within the same database transaction as the `journal_entry`, write an event payload to an `outbox_events` table (`id`, `aggregate_type`, `payload`, `status = 'PENDING'`).
2. If the database transaction rolls back, the outbox record rolls back with it—guaranteeing zero phantom messages.
3. A separate asynchronous process (such as Debezium CDC reading PostgreSQL Write-Ahead Logs, or a polling publisher with `@Scheduled`) streams committed outbox events to Kafka with at-least-once delivery guarantees.

---

## 6. What Was Deliberately Left Out & Why

* **Authentication / User Accounts**: Explicitly excluded per assignment specification (`X-Tenant-Id` header is sufficient).
* **Chart of Accounts / Account Validation**: Treated as plain strings per instructions.
* **Currency Conversion**: Assumed one currency per tenant per instructions.
* **Frontend UI**: Strictly a headless backend REST service per instructions.

---

## 7. What I Would Do Next With More Time

1. **PostgreSQL Row-Level Security (RLS)**: Enforce DB-level RLS policies with `SET LOCAL app.current_tenant = :tenant` on each connection so that even raw native SQL queries cannot bypass tenant boundaries.
2. **Account Balance Snapshots**: Currently, balances are calculated via SQL aggregation `SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE -amount END)`. For accounts with millions of transactions, periodic account balance checkpoints (e.g., daily closing balances) would allow querying only delta entries since the last snapshot.
3. **Distributed Redis Idempotency Lock**: Replace database-level polling for in-flight requests with Redis distributed locks (`Redisson`), freeing database connections.
