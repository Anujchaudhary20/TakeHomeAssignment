package com.ledger.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.ledger.dto.CreateJournalEntryRequest;
import com.ledger.ledger.dto.JournalEntryLineRequest;
import com.ledger.ledger.dto.JournalEntryResponse;
import com.ledger.ledger.model.Direction;
import com.ledger.ledger.repository.JournalEntryRepository;
import com.ledger.tenant.TenantContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JournalEntryReversalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private static final String TENANT = "tenant-reversal-test";

    @BeforeEach
    void setUp() {
        TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.deleteAll());
    }

    @Test
    @DisplayName("Section 3.4: Reversing an entry creates mirrored swapping entry, brings balance to zero, and blocks double reversal")
    void testReversalWorkflowAndDoubleReversalRejection() throws Exception {
        // Step 1: Create original entry (Debit Bank 10,000, Credit Sales 10,000)
        CreateJournalEntryRequest originalRequest = new CreateJournalEntryRequest(
                LocalDate.of(2026, 9, 15),
                "Customer payment received",
                List.of(
                        new JournalEntryLineRequest("1000-BANK", new BigDecimal("10000.00"), Direction.DEBIT),
                        new JournalEntryLineRequest("4000-SALES", new BigDecimal("10000.00"), Direction.CREDIT)
                )
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/journal-entries")
                        .header("X-Tenant-Id", TENANT)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JournalEntryResponse original = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                JournalEntryResponse.class
        );
        UUID originalId = original.id();

        // Verify pre-reversal balance: 10,000.00
        mockMvc.perform(get("/api/v1/accounts/1000-BANK/balance")
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(10000.00)));

        // Step 2: Reverse the entry
        MvcResult reversalResult = mockMvc.perform(post("/api/v1/journal-entries/" + originalId + "/reverse")
                        .header("X-Tenant-Id", TENANT)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversalOfEntryId", is(originalId.toString())))
                .andReturn();

        JournalEntryResponse reversal = objectMapper.readValue(
                reversalResult.getResponse().getContentAsString(),
                JournalEntryResponse.class
        );

        // Verify post-reversal balance: 10,000 - 10,000 = 0.00
        mockMvc.perform(get("/api/v1/accounts/1000-BANK/balance")
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(0.00)));

        // Verify link on original entry
        mockMvc.perform(get("/api/v1/journal-entries/" + originalId)
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversedByEntryId", is(reversal.id().toString())));

        // Step 3: Attempt to reverse the original entry a SECOND time -> Rejected with 409 Conflict
        mockMvc.perform(post("/api/v1/journal-entries/" + originalId + "/reverse")
                        .header("X-Tenant-Id", TENANT)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Journal entry " + originalId + " has already been reversed.")));

        // Step 4: Attempt to reverse the reversal entry itself -> Rejected with 409 Conflict
        mockMvc.perform(post("/api/v1/journal-entries/" + reversal.id() + "/reverse")
                        .header("X-Tenant-Id", TENANT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Cannot reverse a journal entry that is itself a reversal.")));

        // Verify exactly 2 entries in database
        long count = TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.count());
        assertEquals(2, count, "Only original entry and single reversal entry should exist");
    }
}
