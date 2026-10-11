package com.ledger.webhook.dto;

import java.util.UUID;

public record PaymentWebhookResponse(
        String eventId,
        String tenantId,
        String status,
        UUID journalEntryId
) {
}
