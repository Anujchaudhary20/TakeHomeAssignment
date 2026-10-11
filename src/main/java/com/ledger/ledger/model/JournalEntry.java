package com.ledger.ledger.model;

import com.ledger.common.exception.UnbalancedJournalEntryException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entry")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "entry_date", nullable = false, updatable = false)
    private LocalDate entryDate;

    @Column(name = "description", nullable = false, updatable = false)
    private String description;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;

    @Column(name = "reversed_by_entry_id")
    private UUID reversedByEntryId;

    @Column(name = "reversal_of_entry_id", updatable = false)
    private UUID reversalOfEntryId;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("lineOrder ASC")
    private List<JournalEntryLine> lines = new ArrayList<>();

    public JournalEntry(UUID id, String tenantId, LocalDate entryDate, String description, Instant postedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.tenantId = tenantId;
        this.entryDate = entryDate;
        this.description = description;
        this.postedAt = postedAt != null ? postedAt : Instant.now();
    }

    public void addLine(String accountCode, BigDecimal amount, Direction direction) {
        int order = this.lines.size() + 1;
        JournalEntryLine line = new JournalEntryLine(UUID.randomUUID(), this, this.tenantId, accountCode, amount, direction, order);
        this.lines.add(line);
    }

    public BigDecimal calculateTotalDebits() {
        return lines.stream()
                .filter(l -> l.getDirection() == Direction.DEBIT)
                .map(JournalEntryLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateTotalCredits() {
        return lines.stream()
                .filter(l -> l.getDirection() == Direction.CREDIT)
                .map(JournalEntryLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
    }

    public void validateBalancing() {
        if (lines == null || lines.size() < 2) {
            throw new UnbalancedJournalEntryException("A journal entry must contain at least two lines.");
        }
        for (JournalEntryLine line : lines) {
            if (line.getAmount() == null || line.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new UnbalancedJournalEntryException("Line amounts must be strictly greater than zero for account: " + line.getAccountCode());
            }
            if (line.getAccountCode() == null || line.getAccountCode().trim().isEmpty()) {
                throw new UnbalancedJournalEntryException("Account code cannot be empty.");
            }
        }
        BigDecimal debits = calculateTotalDebits();
        BigDecimal credits = calculateTotalCredits();
        if (debits.compareTo(credits) != 0) {
            throw new UnbalancedJournalEntryException(String.format(
                    "Journal entry is unbalanced. Total debits (%s) must equal total credits (%s). Difference: %s",
                    debits.setScale(2, RoundingMode.HALF_UP),
                    credits.setScale(2, RoundingMode.HALF_UP),
                    debits.subtract(credits).setScale(2, RoundingMode.HALF_UP)
            ));
        }
    }

    public boolean isReversed() {
        return reversedByEntryId != null;
    }

    public boolean isReversal() {
        return reversalOfEntryId != null;
    }
}
