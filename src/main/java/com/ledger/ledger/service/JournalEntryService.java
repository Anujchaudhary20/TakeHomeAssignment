package com.ledger.ledger.service;

import com.ledger.common.exception.ConflictException;
import com.ledger.common.exception.ResourceNotFoundException;
import com.ledger.ledger.dto.*;
import com.ledger.ledger.model.Direction;
import com.ledger.ledger.model.JournalEntry;
import com.ledger.ledger.model.JournalEntryLine;
import com.ledger.ledger.repository.JournalEntryRepository;
import com.ledger.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalEntryService {

    private final JournalEntryRepository journalEntryRepository;

    @Transactional
    public JournalEntryResponse createJournalEntry(CreateJournalEntryRequest request) {
        String tenantId = TenantContext.getRequiredTenantId();

        JournalEntry entry = new JournalEntry(
                UUID.randomUUID(),
                tenantId,
                request.entryDate(),
                request.description(),
                Instant.now()
        );

        for (JournalEntryLineRequest lineReq : request.lines()) {
            entry.addLine(lineReq.accountCode().trim(), lineReq.amount(), lineReq.direction());
        }

        // Domain invariant check: Sum(Debits) == Sum(Credits)
        entry.validateBalancing();

        JournalEntry saved = journalEntryRepository.save(entry);
        log.info("Created journal entry id={} for tenant={}", saved.getId(), tenantId);
        return JournalEntryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getJournalEntry(UUID id) {
        String tenantId = TenantContext.getRequiredTenantId();

        // Hibernate @TenantId scopes the query; findByIdAndTenantId provides defense-in-depth
        JournalEntry entry = journalEntryRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found with id: " + id));

        return JournalEntryResponse.from(entry);
    }

    @Transactional(readOnly = true)
    public Page<JournalEntryResponse> listJournalEntries(LocalDate from, LocalDate to, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' date cannot be after 'to' date");
        }

        Page<JournalEntry> page = journalEntryRepository.findByDateRange(from, to, pageable);
        return page.map(JournalEntryResponse::from);
    }

    @Transactional
    public JournalEntryResponse reverseJournalEntry(UUID originalId) {
        String tenantId = TenantContext.getRequiredTenantId();

        JournalEntry original = journalEntryRepository.findByIdAndTenantId(originalId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found with id: " + originalId));

        if (original.isReversal()) {
            throw new ConflictException("Cannot reverse a journal entry that is itself a reversal.");
        }

        // Prevent double reversal: Check if a reversal entry already exists
        if (journalEntryRepository.existsByReversalOfEntryId(originalId)) {
            throw new ConflictException("Journal entry " + originalId + " has already been reversed.");
        }

        // Create new reversing entry with swapped debits and credits
        JournalEntry reversal = new JournalEntry(
                UUID.randomUUID(),
                tenantId,
                LocalDate.now(),
                "Reversal of entry " + original.getId() + " - " + original.getDescription(),
                Instant.now()
        );
        reversal.setReversalOfEntryId(original.getId());

        for (JournalEntryLine line : original.getLines()) {
            Direction swappedDirection = (line.getDirection() == Direction.DEBIT)
                    ? Direction.CREDIT
                    : Direction.DEBIT;
            reversal.addLine(line.getAccountCode(), line.getAmount(), swappedDirection);
        }

        reversal.validateBalancing();
        JournalEntry savedReversal = journalEntryRepository.save(reversal);

        // Record the link on original entry as well
        original.setReversedByEntryId(savedReversal.getId());
        journalEntryRepository.save(original);

        log.info("Reversed journal entry id={} with new entry id={} for tenant={}", originalId, savedReversal.getId(), tenantId);
        return JournalEntryResponse.from(savedReversal);
    }
}
