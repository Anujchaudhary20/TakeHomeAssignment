package com.ledger.ledger.repository;

import com.ledger.ledger.model.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Optional<JournalEntry> findByIdAndTenantId(UUID id, String tenantId);

    @Query("""
        SELECT e FROM JournalEntry e
        WHERE (:from IS NULL OR e.entryDate >= :from)
          AND (:to IS NULL OR e.entryDate <= :to)
    """)
    Page<JournalEntry> findByDateRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable
    );

    Page<JournalEntry> findByEntryDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    Page<JournalEntry> findByEntryDateGreaterThanEqual(LocalDate from, Pageable pageable);

    Page<JournalEntry> findByEntryDateLessThanEqual(LocalDate to, Pageable pageable);

    boolean existsByReversalOfEntryId(UUID reversalOfEntryId);
}
