package com.ledger.ledger.controller;

import com.ledger.ledger.dto.AccountBalanceResponse;
import com.ledger.ledger.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final BalanceService balanceService;

    @GetMapping("/{accountCode}/balance")
    public ResponseEntity<AccountBalanceResponse> getAccountBalance(
            @PathVariable String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        AccountBalanceResponse response = balanceService.getAccountBalance(accountCode, asOf);
        return ResponseEntity.ok(response);
    }
}
