package com.retailpulse.payment.controller;

import com.retailpulse.payment.payloads.PaymentData;
import com.retailpulse.payment.payloads.PaymentResponse;
import com.retailpulse.payment.service.PaymentService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:4200", maxAge = 3600)
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping(path = "/create-payment-intent", consumes = "application/json", produces = "application/json")
    public ResponseEntity<PaymentResponse> createPaymentIntent(@RequestBody PaymentData data) throws StripeException {
        PaymentResponse response = paymentService.createPaymentIntent(data);
        return ResponseEntity.ok(response);
    }
}
