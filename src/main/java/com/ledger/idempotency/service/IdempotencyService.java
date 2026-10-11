package com.ledger.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.common.exception.ConflictException;
import com.ledger.common.exception.IdempotencyPayloadMismatchException;
import com.ledger.idempotency.model.IdempotencyRecord;
import com.ledger.idempotency.model.IdempotencyStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordReservationService reservationService;
    private final ObjectMapper objectMapper;

    private static final int MAX_WAIT_MILLIS = 3000;
    private static final int POLL_INTERVAL_MILLIS = 100;

    public <T> ResponseEntity<T> executeIdempotent(
            String tenantId,
            String idempotencyKey,
            Object requestPayload,
            Class<T> responseType,
            Supplier<ResponseEntity<T>> businessLogic
    ) {
        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return businessLogic.get();
        }

        String normalizedKey = idempotencyKey.trim();
        String requestHash = computeHash(requestPayload);

        IdempotencyRecord record;
        try {
            record = reservationService.reserve(tenantId, normalizedKey, requestHash);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Concurrent insertion race detected for tenant={} idempotencyKey={}", tenantId, normalizedKey);
            return handleExistingRecord(tenantId, normalizedKey, requestHash, responseType);
        }

        try {
            ResponseEntity<T> response = businessLogic.get();
            String serializedResponse = objectMapper.writeValueAsString(response.getBody());
            reservationService.markCompleted(record.getId(), response.getStatusCode().value(), serializedResponse);
            return response;
        } catch (Exception ex) {
            reservationService.markFailedOrRemove(record.getId());
            throw (ex instanceof RuntimeException re) ? re : new RuntimeException(ex);
        }
    }

    private <T> ResponseEntity<T> handleExistingRecord(
            String tenantId,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType
    ) {
        // Wait / poll for the in-flight request to finish or read immediately if already completed
        int waited = 0;
        while (waited <= MAX_WAIT_MILLIS) {
            Optional<IdempotencyRecord> recordOpt = reservationService.findRecord(tenantId, idempotencyKey);
            if (recordOpt.isPresent()) {
                IdempotencyRecord existing = recordOpt.get();

                if (!existing.getRequestHash().equals(requestHash)) {
                    throw new IdempotencyPayloadMismatchException(
                            "Idempotency key '" + idempotencyKey + "' was already used with a different request payload."
                    );
                }

                if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
                    return buildCachedResponse(existing, responseType);
                }
            }

            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConflictException("Interrupted while waiting for in-flight idempotent request.");
            }
            waited += POLL_INTERVAL_MILLIS;
        }

        throw new ConflictException("A request with Idempotency-Key '" + idempotencyKey + "' is still processing. Please retry.");
    }

    private <T> ResponseEntity<T> buildCachedResponse(IdempotencyRecord record, Class<T> responseType) {
        try {
            T body = objectMapper.readValue(record.getResponseBody(), responseType);
            int status = record.getResponseStatus() != null ? record.getResponseStatus() : 200;
            return ResponseEntity.status(status).body(body);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cached idempotent response body", e);
            throw new RuntimeException("Error reading cached response for idempotency key: " + record.getIdempotencyKey(), e);
        }
    }

    public String computeHash(Object payload) {
        try {
            String json = (payload instanceof String s) ? s : objectMapper.writeValueAsString(payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new RuntimeException("Failed to compute SHA-256 request payload hash", e);
        }
    }
}
