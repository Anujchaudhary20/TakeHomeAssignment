package com.ledger.webhook.service;

import com.ledger.ledger.model.Direction;
import com.ledger.ledger.model.JournalEntry;
import com.ledger.ledger.repository.JournalEntryRepository;
import com.ledger.tenant.TenantContext;
import com.ledger.webhook.dto.PaymentWebhookRequest;
import com.ledger.webhook.dto.PaymentWebhookResponse;
import com.ledger.webhook.model.WebhookInboxEvent;
import com.ledger.webhook.model.WebhookStatus;
import com.ledger.webhook.repository.WebhookInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final WebhookInboxRepository webhookInboxRepository;
    private final JournalEntryRepository journalEntryRepository;

    @Value("${ledger.webhook.default-bank-account:1000-BANK}")
    private String defaultBankAccount;

    @Value("${ledger.webhook.default-revenue-account:4000-SALES}")
    private String defaultRevenueAccount;

    @Transactional
    public PaymentWebhookResponse processPayment(PaymentWebhookRequest request) {
        // Step 1: Check for deduplication (Situation 2: two deliveries of evt must produce exactly one journal entry)
        Optional<WebhookInboxEvent> existing = webhookInboxRepository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            WebhookInboxEvent event = existing.get();
            if (event.getStatus() == WebhookStatus.PROCESSED) {
                log.info("Idempotent duplicate webhook delivery: eventId={} already processed", request.eventId());
                return new PaymentWebhookResponse(
                        request.eventId(),
                        request.tenantId(),
                        "ALREADY_PROCESSED",
                        event.getJournalEntry() != null ? event.getJournalEntry().getId() : null
                );
            }
        }

        // Step 2: Record event in Webhook Inbox (Situation 4: inbox pattern prevents silent vanishing)
        WebhookInboxEvent inboxEvent = existing.orElseGet(() -> new WebhookInboxEvent(
                UUID.randomUUID(),
                request.eventId(),
                request.tenantId(),
                request.amount(),
                request.currency(),
                request.settledAt(),
                WebhookStatus.RECEIVED
        ));

        try {
            // Step 3: Create Journal Entry in tenant context
            LocalDate entryDate = request.settledAt().atZone(ZoneOffset.UTC).toLocalDate();

            JournalEntry entry = TenantContext.runWithTenant(request.tenantId(), () -> {
                JournalEntry journalEntry = new JournalEntry(
                        UUID.randomUUID(),
                        request.tenantId(),
                        entryDate,
                        "Settled payment " + request.eventId(),
                        Instant.now()
                );

                // Debit Bank, Credit Sales Revenue
                journalEntry.addLine(defaultBankAccount, request.amount(), Direction.DEBIT);
                journalEntry.addLine(defaultRevenueAccount, request.amount(), Direction.CREDIT);

                journalEntry.validateBalancing();
                return journalEntryRepository.save(journalEntry);
            });

            // Step 4: Link Journal Entry and mark PROCESSED
            inboxEvent.setJournalEntry(entry);
            inboxEvent.setStatus(WebhookStatus.PROCESSED);
            webhookInboxRepository.save(inboxEvent);

            log.info("Successfully recorded webhook eventId={} as journalEntryId={} for tenant={}",
                    request.eventId(), entry.getId(), request.tenantId());

            return new PaymentWebhookResponse(
                    request.eventId(),
                    request.tenantId(),
                    "PROCESSED",
                    entry.getId()
            );
        } catch (Exception ex) {
            log.error("Failed to process payment webhook eventId={}", request.eventId(), ex);
            inboxEvent.setStatus(WebhookStatus.FAILED);
            inboxEvent.setFailureReason(ex.getMessage());
            inboxEvent.setRetryCount(inboxEvent.getRetryCount() + 1);
            webhookInboxRepository.save(inboxEvent);
            throw (ex instanceof RuntimeException re) ? re : new RuntimeException(ex);
        }
    }
}
