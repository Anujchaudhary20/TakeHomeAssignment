package com.ledger.ledger.service;

import com.ledger.ledger.dto.AccountBalanceResponse;
import com.ledger.ledger.repository.JournalEntryLineRepository;
import com.ledger.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final JournalEntryLineRepository journalEntryLineRepository;

    @Transactional(readOnly = true)
    public AccountBalanceResponse getAccountBalance(String accountCode, LocalDate asOf) {
        String tenantId = TenantContext.getRequiredTenantId();
        BigDecimal balance = journalEntryLineRepository.calculateAccountBalance(tenantId, accountCode.trim(), asOf);

        if (balance == null) {
            balance = BigDecimal.ZERO;
        }

        return new AccountBalanceResponse(
                accountCode.trim(),
                tenantId,
                balance.setScale(2, RoundingMode.HALF_UP),
                asOf
        );
    }
}
