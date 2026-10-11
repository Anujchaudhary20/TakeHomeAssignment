package com.ledger.ledger.controller;

import com.ledger.idempotency.service.IdempotencyService;
import com.ledger.ledger.dto.CreateJournalEntryRequest;
import com.ledger.ledger.dto.JournalEntryResponse;
import com.ledger.ledger.service.JournalEntryService;
import com.ledger.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/journal-entries")
@RequiredArgsConstructor
public class JournalEntryController {

    private final JournalEntryService journalEntryService;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<JournalEntryResponse> createJournalEntry(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateJournalEntryRequest request
    ) {
        String tenantId = TenantContext.getRequiredTenantId();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyService.executeIdempotent(
                    tenantId,
                    idempotencyKey,
                    request,
                    JournalEntryResponse.class,
                    () -> ResponseEntity.status(HttpStatus.CREATED).body(journalEntryService.createJournalEntry(request))
            );
        }

        JournalEntryResponse response = journalEntryService.createJournalEntry(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<JournalEntryResponse>> listJournalEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(page = 0, size = 20, sort = "entryDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<JournalEntryResponse> page = journalEntryService.listJournalEntries(from, to, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JournalEntryResponse> getJournalEntry(@PathVariable UUID id) {
        JournalEntryResponse response = journalEntryService.getJournalEntry(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<JournalEntryResponse> reverseJournalEntry(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        String tenantId = TenantContext.getRequiredTenantId();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyService.executeIdempotent(
                    tenantId,
                    idempotencyKey,
                    "reverse:" + id,
                    JournalEntryResponse.class,
                    () -> ResponseEntity.status(HttpStatus.CREATED).body(journalEntryService.reverseJournalEntry(id))
            );
        }

        JournalEntryResponse response = journalEntryService.reverseJournalEntry(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
