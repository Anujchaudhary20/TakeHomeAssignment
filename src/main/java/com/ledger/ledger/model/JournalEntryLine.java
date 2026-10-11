package com.ledger.ledger.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "journal_entry_line")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntryLine {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journal_entry_id", nullable = false, updatable = false)
    private JournalEntry journalEntry;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "account_code", nullable = false, updatable = false)
    private String accountCode;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10, updatable = false)
    private Direction direction;

    @Column(name = "line_order", nullable = false, updatable = false)
    private Integer lineOrder;

    public JournalEntryLine(UUID id, JournalEntry journalEntry, String tenantId, String accountCode, BigDecimal amount, Direction direction, Integer lineOrder) {
        this.id = id != null ? id : UUID.randomUUID();
        this.journalEntry = journalEntry;
        this.tenantId = tenantId;
        this.accountCode = accountCode;
        this.amount = amount;
        this.direction = direction;
        this.lineOrder = lineOrder;
    }
}
