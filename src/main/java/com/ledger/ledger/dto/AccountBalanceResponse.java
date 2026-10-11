package com.ledger.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountBalanceResponse(
        String accountCode,
        String tenantId,
        BigDecimal balance,
        LocalDate asOf
) {
}
