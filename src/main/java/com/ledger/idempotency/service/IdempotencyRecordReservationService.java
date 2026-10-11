package com.ledger.idempotency.service;

import com.ledger.idempotency.model.IdempotencyRecord;
import com.ledger.idempotency.model.IdempotencyStatus;
import com.ledger.idempotency.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyRecordReservationService {

    private final IdempotencyRecordRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyRecord reserve(String tenantId, String key, String requestHash) {
        IdempotencyRecord record = new IdempotencyRecord(
                UUID.randomUUID(),
                tenantId,
                key,
                requestHash,
                IdempotencyStatus.IN_PROGRESS
        );
        return repository.saveAndFlush(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(UUID recordId, int responseStatus, String responseBody) {
        repository.findById(recordId).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseStatus(responseStatus);
            record.setResponseBody(responseBody);
            record.setCompletedAt(Instant.now());
            repository.saveAndFlush(record);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedOrRemove(UUID recordId) {
        try {
            repository.deleteById(recordId);
        } catch (Exception ex) {
            log.warn("Failed to clean up idempotency record id={}", recordId, ex);
        }
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findRecord(String tenantId, String key) {
        return repository.findByTenantIdAndIdempotencyKey(tenantId, key);
    }
}
