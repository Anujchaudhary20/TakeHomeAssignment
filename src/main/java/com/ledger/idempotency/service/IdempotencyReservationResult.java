package com.ledger.idempotency.service;

import com.ledger.idempotency.model.IdempotencyRecord;

public record IdempotencyReservationResult(
        boolean acquired,
        IdempotencyRecord record
) {
    public static IdempotencyReservationResult acquired(IdempotencyRecord record) {
        return new IdempotencyReservationResult(true, record);
    }

    public static IdempotencyReservationResult duplicate() {
        return new IdempotencyReservationResult(false, null);
    }
}
