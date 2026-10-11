# AI Usage Statement

## Overview
In accordance with Section 7 and 8 of the assignment instructions, this document provides an honest, transparent account of how AI assistance was leveraged during the development of the Multi-Tenant Accounting Ledger Service.

---

## 1. AI Tools Used
* **Google Gemini / Antigravity Agentic Pair Programmer**
* Used for architectural validation, code scaffolding, concurrency design review, and edge-case test generation.

---

## 2. Where AI Was Used

1. **Architecture & Schema Design**:
   * Scaffolding Flyway SQL migration scripts (`V1`, `V2`, `V3`) and JPA entity definitions.
   * Reviewing database indexing strategies for multi-tenant compound queries (`(tenant_id, entry_date)` and `(tenant_id, idempotency_key)`).

2. **Concurrency & Race Condition Handling**:
   * Brainstorming race condition mitigation strategies for identical requests arriving at the same millisecond.
   * Designing the `CountDownLatch` multi-threaded stress test in `JournalEntryConcurrencyAndIdempotencyTest`.

3. **HMAC-SHA256 Webhook Verification**:
   * Reviewing best practices for raw-byte hashing in Java Servlets and timing-attack-resistant comparisons (`MessageDigest.isEqual`).

---

## 3. Where AI Output Was Corrected or Discarded

The primary value of engineering experience is knowing when AI-generated code is flawed. During this project, three critical corrections were made to AI suggestions:

### Correction 1: Spring JPA Transaction Rollback-Only Behavior
* **What AI Suggested**: The AI initially generated a single `@Transactional(propagation = Propagation.REQUIRES_NEW)` method that wrapped the insert in a `try-catch(DataIntegrityViolationException)` and attempted to return a fallback object.
* **Why It Was Flawed**: In Spring JPA, when a `saveAndFlush()` fails due to a database constraint violation, Hibernate marks the underlying physical transaction as **rollback-only**. When the method exits and Spring attempts to commit the transaction, it throws an `UnexpectedRollbackException: Transaction silently rolled back because it has been marked as rollback-only`.
* **The Fix**: Refactored the architecture so that `IdempotencyRecordReservationService.reserve()` allows the `REQUIRES_NEW` transaction to roll back naturally upon catching the duplicate key violation, and the outer `IdempotencyService` catches the exception cleanly across transaction boundaries.

### Correction 2: Hibernate 6 `@TenantId` Session Resolution in Webhooks
* **What AI Suggested**: The AI initially placed `TenantContext.runWithTenant(...)` *inside* the `@Transactional PaymentWebhookService.processPayment()` method.
* **Why It Was Flawed**: Webhooks do not include the `X-Tenant-Id` header (the `tenantId` is contained in the signed JSON body). If `TenantContext` is not set before the Spring `@Transactional` interceptor fires, Hibernate resolves the tenant at session initialization as `SYSTEM_UNASSIGNED`. When the service then attempts to persist a `JournalEntry` with `tenantId = 'acme-corp'`, Hibernate 6 detects a tenant identifier mismatch between the session and the entity and aborts.
* **The Fix**: Explicitly populated `TenantContext.setTenantId(request.tenantId())` in `PaymentWebhookController` *before* the transaction starts, and cleared it in a `finally` block.

### Correction 3: Raw Byte Caching in Servlet Request Wrappers
* **What AI Suggested**: The AI initially recommended using Spring's standard `ContentCachingRequestWrapper`.
* **Why It Was Flawed**: `ContentCachingRequestWrapper` only populates its internal cache *after* an application component reads the stream. If an HMAC verification filter runs first and attempts to read `getContentAsByteArray()`, it returns an empty array.
* **The Fix**: Discarded `ContentCachingRequestWrapper` and authored a dedicated `CachedBodyHttpServletRequest` that eagerly buffers the `InputStream` bytes upon instantiation in the constructor and provides a new `ByteArrayInputStream` for subsequent reads by Jackson's `@RequestBody`.
