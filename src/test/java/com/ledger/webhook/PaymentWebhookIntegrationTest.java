package com.ledger.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.ledger.model.JournalEntry;
import com.ledger.ledger.repository.JournalEntryRepository;
import com.ledger.tenant.TenantContext;
import com.ledger.webhook.dto.PaymentWebhookRequest;
import com.ledger.webhook.filter.HmacSignatureFilter;
import com.ledger.webhook.model.WebhookInboxEvent;
import com.ledger.webhook.model.WebhookStatus;
import com.ledger.webhook.repository.WebhookInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentWebhookIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private WebhookInboxRepository webhookInboxRepository;

    @Value("${ledger.webhook.secret:test-webhook-secret-key-123}")
    private String webhookSecret;

    private static final String TENANT = "acme-corp";

    @BeforeEach
    void setUp() {
        webhookInboxRepository.deleteAll();
        TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.deleteAll());
    }

    @Test
    @DisplayName("Mandatory Requirement 4: The same payment notification delivered twice creates only one journal entry")
    void testWebhookDeduplication() throws Exception {
        String eventId = "evt_9f2b7c41";
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                eventId,
                TENANT,
                new BigDecimal("2500.00"),
                "INR",
                Instant.parse("2026-09-15T10:32:00Z")
        );

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        String signature = "sha256=" + HmacSignatureFilter.calculateHmacSha256Hex(rawBody, webhookSecret);

        // First delivery: processes payment, creates journal entry, returns 200 OK
        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PROCESSED")))
                .andExpect(jsonPath("$.eventId", is(eventId)));

        long countFirst = TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.count());
        assertEquals(1, countFirst, "First delivery must create exactly 1 journal entry");

        // Second delivery (e.g. payment provider retry)
        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ALREADY_PROCESSED")))
                .andExpect(jsonPath("$.eventId", is(eventId)));

        // Verify deduplication: exactly 1 journal entry remains
        long countSecond = TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.count());
        assertEquals(1, countSecond, "Duplicate delivery must NOT create a second journal entry");

        // Verify account balance reflects single payment (2500.00 in 1000-BANK)
        mockMvc.perform(get("/api/v1/accounts/1000-BANK/balance")
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(2500.00)));

        // Verify inbox event status
        Optional<WebhookInboxEvent> inboxEvent = webhookInboxRepository.findByEventId(eventId);
        assertTrue(inboxEvent.isPresent());
        assertEquals(WebhookStatus.PROCESSED, inboxEvent.get().getStatus());
    }

    @Test
    @DisplayName("Webhook with invalid HMAC signature is rejected with 401 Unauthorized and nothing is written")
    void testInvalidHmacSignatureIsRejected() throws Exception {
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                "evt_tampered_123",
                TENANT,
                new BigDecimal("9999.00"),
                "INR",
                Instant.now()
        );

        byte[] rawBody = objectMapper.writeValueAsBytes(request);
        String invalidSignature = "sha256=0000000000000000000000000000000000000000000000000000000000000000";

        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .header("X-Signature", invalidSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Invalid HMAC signature")));

        long count = TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.count());
        assertEquals(0, count, "Tampered webhook must not write to database");
        assertEquals(0, webhookInboxRepository.count(), "Tampered webhook must not write to inbox");
    }

    @Test
    @DisplayName("Webhook with missing X-Signature header is rejected with 401 Unauthorized")
    void testMissingHmacSignatureIsRejected() throws Exception {
        PaymentWebhookRequest request = new PaymentWebhookRequest(
                "evt_no_signature",
                TENANT,
                new BigDecimal("100.00"),
                "INR",
                Instant.now()
        );

        mockMvc.perform(post("/api/v1/webhooks/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isUnauthorized());

        long count = TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.count());
        assertEquals(0, count);
    }
}
