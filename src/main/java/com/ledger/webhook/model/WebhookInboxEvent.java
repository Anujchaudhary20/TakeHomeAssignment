package com.ledger.webhook.model;

import com.ledger.ledger.model.JournalEntry;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_inbox")
@Getter
@Setter
@NoArgsConstructor
public class WebhookInboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private String eventId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10, updatable = false)
    private String currency;

    @Column(name = "settled_at", nullable = false, updatable = false)
    private Instant settledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private WebhookStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public WebhookInboxEvent(UUID id, String eventId, String tenantId, BigDecimal amount, String currency, Instant settledAt, WebhookStatus status) {
        this.id = id != null ? id : UUID.randomUUID();
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.amount = amount;
        this.currency = currency;
        this.settledAt = settledAt;
        this.status = status;
        this.receivedAt = Instant.now();
        this.retryCount = 0;
    }
}
