package com.ledger.ledger.dto;

import com.ledger.ledger.model.JournalEntry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JournalEntryResponse(
        UUID id,
        String tenantId,
        LocalDate entryDate,
        String description,
        Instant postedAt,
        UUID reversedByEntryId,
        UUID reversalOfEntryId,
        List<JournalEntryLineResponse> lines,
        BigDecimal totalDebits,
        BigDecimal totalCredits
) {
    public static JournalEntryResponse from(JournalEntry entry) {
        List<JournalEntryLineResponse> lineResponses = entry.getLines().stream()
                .map(JournalEntryLineResponse::from)
                .toList();

        return new JournalEntryResponse(
                entry.getId(),
                entry.getTenantId(),
                entry.getEntryDate(),
                entry.getDescription(),
                entry.getPostedAt(),
                entry.getReversedByEntryId(),
                entry.getReversalOfEntryId(),
                lineResponses,
                entry.calculateTotalDebits().setScale(2, RoundingMode.HALF_UP),
                entry.calculateTotalCredits().setScale(2, RoundingMode.HALF_UP)
        );
    }
}
