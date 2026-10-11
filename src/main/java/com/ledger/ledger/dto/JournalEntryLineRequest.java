package com.ledger.ledger.dto;

import com.ledger.ledger.model.Direction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record JournalEntryLineRequest(
        @NotBlank(message = "Account code is required")
        String accountCode,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be strictly positive")
        BigDecimal amount,

        @NotNull(message = "Direction is required (DEBIT or CREDIT)")
        Direction direction
) {
}
