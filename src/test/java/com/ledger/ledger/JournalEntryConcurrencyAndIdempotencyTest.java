package com.ledger.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.idempotency.repository.IdempotencyRecordRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JournalEntryConcurrencyAndIdempotencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private static final String TENANT = "tenant-idempotency-test";

    @BeforeEach
    void setUp() {
        TenantContext.runWithTenant(TENANT, () -> {
            journalEntryRepository.deleteAll();
            idempotencyRecordRepository.deleteAll();
        });
    }

    @Test
    @DisplayName("Mandatory Requirement 2: Sending the same request twice creates only one entry (Sequential Retry)")
    void testSequentialSameRequestCreatesOnlyOneEntry() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        CreateJournalEntryRequest request = new CreateJournalEntryRequest(
                LocalDate.of(2026, 9, 15),
                "Idempotent payment test",
                List.of(
                        new JournalEntryLineRequest("1000-BANK", new BigDecimal("500.00"), Direction.DEBIT),
                        new JournalEntryLineRequest("4000-SALES", new BigDecimal("500.00"), Direction.CREDIT)
                )
        );

        // First delivery
        MvcResult firstResult = mockMvc.perform(post("/api/v1/journal-entries")
                        .header("X-Tenant-Id", TENANT)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JournalEntryResponse firstResponse = objectMapper.readValue(
                firstResult.getResponse().getContentAsString(),
                JournalEntryResponse.class
        );

        // Second delivery (e.g. after network timeout)
        MvcResult secondResult = mockMvc.perform(post("/api/v1/journal-entries")
                        .header("X-Tenant-Id", TENANT)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();

        int secondStatus = secondResult.getResponse().getStatus();
        // Returns 201 or 200 with identical cached response
        assertEquals(true, secondStatus == 201 || secondStatus == 200);

        JournalEntryResponse secondResponse = objectMapper.readValue(
                secondResult.getResponse().getContentAsString(),
                JournalEntryResponse.class
        );

        assertEquals(firstResponse.id(), secondResponse.id(), "Must return identical entry ID");
        long count = TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.count());
        assertEquals(1, count, "Exactly one journal entry must be present in the database");
    }

    @Test
    @DisplayName("Mandatory Requirement 2: Concurrent requests arriving at the same millisecond create only one entry")
    void testConcurrentIdenticalRequestsCreateOnlyOneEntry() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        CreateJournalEntryRequest request = new CreateJournalEntryRequest(
                LocalDate.of(2026, 9, 15),
                "Concurrent race condition test",
                List.of(
                        new JournalEntryLineRequest("1000-BANK", new BigDecimal("1200.00"), Direction.DEBIT),
                        new JournalEntryLineRequest("4000-SALES", new BigDecimal("1200.00"), Direction.CREDIT)
                )
        );

        String jsonPayload = objectMapper.writeValueAsString(request);
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(threadCount);
        List<MvcResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startGun.await(); // Synchronize all threads to fire simultaneously
                    MvcResult result = mockMvc.perform(post("/api/v1/journal-entries")
                                    .header("X-Tenant-Id", TENANT)
                                    .header("Idempotency-Key", idempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonPayload))
                            .andReturn();
                    results.add(result);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    finishLine.countDown();
                }
            });
        }

        // Fire all threads at once
        startGun.countDown();
        finishLine.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // Verify results
        assertEquals(threadCount, results.size());
        UUID commonEntryId = null;

        for (MvcResult res : results) {
            int status = res.getResponse().getStatus();
            assertEquals(true, status == 201 || status == 200, "Every thread should receive success");
            JournalEntryResponse response = objectMapper.readValue(
                    res.getResponse().getContentAsString(),
                    JournalEntryResponse.class
            );
            if (commonEntryId == null) {
                commonEntryId = response.id();
            } else {
                assertEquals(commonEntryId, response.id(), "All concurrent requests must resolve to the identical entry ID");
            }
        }

        assertNotNull(commonEntryId);
        long count = TenantContext.runWithTenant(TENANT, () -> journalEntryRepository.count());
        assertEquals(1, count, "Race condition handled: Database must contain exactly 1 journal entry");
    }

    @Test
    @DisplayName("Reusing an Idempotency-Key with a different payload is rejected")
    void testReusingIdempotencyKeyWithDifferentPayloadIsRejected() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        CreateJournalEntryRequest request1 = new CreateJournalEntryRequest(
                LocalDate.now(),
                "First payload",
                List.of(
                        new JournalEntryLineRequest("1000-BANK", new BigDecimal("100.00"), Direction.DEBIT),
                        new JournalEntryLineRequest("4000-SALES", new BigDecimal("100.00"), Direction.CREDIT)
                )
        );

        CreateJournalEntryRequest request2 = new CreateJournalEntryRequest(
                LocalDate.now(),
                "Different payload under same key",
                List.of(
                        new JournalEntryLineRequest("1000-BANK", new BigDecimal("200.00"), Direction.DEBIT),
                        new JournalEntryLineRequest("4000-SALES", new BigDecimal("200.00"), Direction.CREDIT)
                )
        );

        // First call succeeds
        mockMvc.perform(post("/api/v1/journal-entries")
                        .header("X-Tenant-Id", TENANT)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // Second call with different payload under same key must be rejected
        mockMvc.perform(post("/api/v1/journal-entries")
                        .header("X-Tenant-Id", TENANT)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isUnprocessableEntity());
    }
}
