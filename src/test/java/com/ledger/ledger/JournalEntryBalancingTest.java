package com.ledger.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.ledger.dto.CreateJournalEntryRequest;
import com.ledger.ledger.dto.JournalEntryLineRequest;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JournalEntryBalancingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    private static final String TENANT = "tenant-balance-test";

    @BeforeEach
    void setUp() {
        TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.deleteAll());
    }

    @Test
    @DisplayName("Mandatory Requirement 1: An entry whose debits and credits do not match is rejected")
    void testUnbalancedEntryIsRejected() throws Exception {
        CreateJournalEntryRequest unbalancedRequest = new CreateJournalEntryRequest(
                LocalDate.now(),
                "Unbalanced payment test",
                List.of(
                        new JournalEntryLineRequest("1000-BANK", new BigDecimal("100.00"), Direction.DEBIT),
                        new JournalEntryLineRequest("4000-SALES", new BigDecimal("90.00"), Direction.CREDIT)
                )
        );

        mockMvc.perform(post("/api/v1/journal-entries")
                        .header("X-Tenant-Id", TENANT)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unbalancedRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Journal entry is unbalanced")))
                .andExpect(jsonPath("$.error", containsString("Total debits (100.00) must equal total credits (90.00)")));

        // Verify nothing reached the database
        long count = TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.count());
        assertEquals(0, count, "Unbalanced entry must never reach the database");
    }

    @Test
    @DisplayName("Balanced entry is successfully accepted and persisted")
    void testBalancedEntryIsCreated() throws Exception {
        CreateJournalEntryRequest balancedRequest = new CreateJournalEntryRequest(
                LocalDate.of(2026, 9, 15),
                "Customer payment received",
                List.of(
                        new JournalEntryLineRequest("1000-BANK", new BigDecimal("10000.00"), Direction.DEBIT),
                        new JournalEntryLineRequest("4000-SALES", new BigDecimal("10000.00"), Direction.CREDIT)
                )
        );

        mockMvc.perform(post("/api/v1/journal-entries")
                        .header("X-Tenant-Id", TENANT)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(balancedRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.tenantId", is(TENANT)))
                .andExpect(jsonPath("$.totalDebits", is(10000.00)))
                .andExpect(jsonPath("$.totalCredits", is(10000.00)))
                .andExpect(jsonPath("$.lines", hasSize(2)));

        long count = TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.count());
        assertEquals(1, count);
    }

    @Test
    @DisplayName("Entry with less than two lines is rejected")
    void testSingleLineEntryIsRejected() throws Exception {
        CreateJournalEntryRequest singleLineRequest = new CreateJournalEntryRequest(
                LocalDate.now(),
                "Invalid single line",
                List.of(
                        new JournalEntryLineRequest("1000-BANK", new BigDecimal("500.00"), Direction.DEBIT)
                )
        );

        mockMvc.perform(post("/api/v1/journal-entries")
                        .header("X-Tenant-Id", TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(singleLineRequest)))
                .andExpect(status().isBadRequest());

        long count = TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.count());
        assertEquals(0, count);
    }
}
