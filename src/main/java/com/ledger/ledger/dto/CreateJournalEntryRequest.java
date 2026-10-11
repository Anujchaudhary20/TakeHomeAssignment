package com.ledger.ledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateJournalEntryRequest(
        @NotNull(message = "Entry date is required")
        LocalDate entryDate,

        @NotBlank(message = "Description is required")
        String description,

        @NotEmpty(message = "Lines cannot be empty")
        @Size(min = 2, message = "A journal entry must have at least two lines")
        @Valid
        List<JournalEntryLineRequest> lines
) {
}
