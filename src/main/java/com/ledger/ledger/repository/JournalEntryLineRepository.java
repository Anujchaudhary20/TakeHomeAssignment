package com.ledger.ledger.repository;

import com.ledger.ledger.model.JournalEntryLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, UUID> {

    @Query("""
        SELECT COALESCE(SUM(CASE WHEN l.direction = com.ledger.ledger.model.Direction.DEBIT THEN l.amount ELSE -l.amount END), 0)
        FROM JournalEntryLine l
        JOIN l.journalEntry e
        WHERE l.accountCode = :accountCode
          AND l.tenantId = :tenantId
          AND (:asOf IS NULL OR e.entryDate <= :asOf)
    """)
    BigDecimal calculateAccountBalance(
            @Param("tenantId") String tenantId,
            @Param("accountCode") String accountCode,
            @Param("asOf") LocalDate asOf
    );
}
