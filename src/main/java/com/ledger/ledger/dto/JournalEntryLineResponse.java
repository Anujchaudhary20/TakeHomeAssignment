package com.ledger.ledger.dto;

import com.ledger.ledger.model.Direction;
import com.ledger.ledger.model.JournalEntryLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public record JournalEntryLineResponse(
        UUID id,
        String accountCode,
        BigDecimal amount,
        Direction direction,
        Integer lineOrder
) {
    public static JournalEntryLineResponse from(JournalEntryLine line) {
        return new JournalEntryLineResponse(
                line.getId(),
                line.getAccountCode(),
                line.getAmount().setScale(2, RoundingMode.HALF_UP),
                line.getDirection(),
                line.getLineOrder()
        );
    }
}
