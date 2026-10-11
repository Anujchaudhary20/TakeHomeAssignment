package com.ledger.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentWebhookRequest(
        @NotBlank(message = "eventId is required")
        String eventId,

        @NotBlank(message = "tenantId is required")
        String tenantId,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be strictly positive")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        String currency,

        @NotNull(message = "settledAt is required")
        Instant settledAt
) {
}
