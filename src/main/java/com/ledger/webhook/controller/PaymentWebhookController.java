package com.ledger.webhook.controller;

import com.ledger.webhook.dto.PaymentWebhookRequest;
import com.ledger.webhook.dto.PaymentWebhookResponse;
import com.ledger.webhook.service.PaymentWebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    @PostMapping("/payments")
    public ResponseEntity<PaymentWebhookResponse> receivePaymentWebhook(
            @Valid @RequestBody PaymentWebhookRequest request
    ) {
        // Return 200 OK strictly after synchronous database commit
        PaymentWebhookResponse response = paymentWebhookService.processPayment(request);
        return ResponseEntity.ok(response);
    }
}
