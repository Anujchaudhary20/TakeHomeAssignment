package com.ledger.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.ledger.dto.CreateJournalEntryRequest;
import com.ledger.ledger.dto.JournalEntryLineRequest;
import com.ledger.ledger.dto.JournalEntryResponse;
import com.ledger.ledger.model.Direction;
import com.ledger.ledger.repository.JournalEntryRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TenantIsolationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private static final String TENANT_A = "acme-corp";
    private static final String TENANT_B = "globex-corp";

    @BeforeEach
    void setUp() {
        journalEntryRepository.deleteAll();
    }

    @Test
    @DisplayName("Mandatory Requirement 3: Tenant B cannot read, reverse, or affect Tenant A's data")
    void testTenantCannotAccessAnotherTenantData() throws Exception {
        // Step 1: Tenant A creates a journal entry
        CreateJournalEntryRequest tenantARequest = new CreateJournalEntryRequest(
                LocalDate.of(2026, 9, 15),
                "Tenant A private payment",
                List.of(
                        new JournalEntryLineRequest("1000-BANK", new BigDecimal("5000.00"), Direction.DEBIT),
                        new JournalEntryLineRequest("4000-SALES", new BigDecimal("5000.00"), Direction.CREDIT)
                )
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/journal-entries")
                        .header("X-Tenant-Id", TENANT_A)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tenantARequest)))
                .andExpect(status().isCreated())
                .andReturn();

        JournalEntryResponse entryA = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                JournalEntryResponse.class
        );
        UUID entryAId = entryA.id();

        // Step 2: Tenant B attempts to FETCH Tenant A's entry by ID
        // Must return 404 NOT FOUND (never 200, and never 403 to avoid leaking ID existence)
        mockMvc.perform(get("/api/v1/journal-entries/" + entryAId)
                        .header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Journal entry not found with id: " + entryAId)));

        // Step 3: Tenant B attempts to REVERSE Tenant A's entry
        mockMvc.perform(post("/api/v1/journal-entries/" + entryAId + "/reverse")
                        .header("X-Tenant-Id", TENANT_B)
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());

        // Verify Tenant A's entry remains unreversed
        mockMvc.perform(get("/api/v1/journal-entries/" + entryAId)
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversedByEntryId").isEmpty());

        // Step 4: Tenant B lists all journal entries -> gets 0 entries
        mockMvc.perform(get("/api/v1/journal-entries")
                        .header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // Step 5: Tenant B checks account balance -> 0.00 balance (unaffected by Tenant A's 5000.00)
        mockMvc.perform(get("/api/v1/accounts/1000-BANK/balance")
                        .header("X-Tenant-Id", TENANT_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(0.00)))
                .andExpect(jsonPath("$.tenantId", is(TENANT_B)));

        // Verify Tenant A's balance is intact
        mockMvc.perform(get("/api/v1/accounts/1000-BANK/balance")
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(5000.00)))
                .andExpect(jsonPath("$.tenantId", is(TENANT_A)));
    }
}
