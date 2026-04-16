package com.retailpulse.payment.controller;

import com.retailpulse.payment.payloads.PaymentData;
import com.retailpulse.payment.payloads.PaymentResponse;
import com.retailpulse.payment.service.PaymentService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.retailpulse.payment.model.Constants.INVALID_SIGNATURE;

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

    // Handle the Stripe webhook event (e.g., payment.succeeded, payment.failed, payment.canceled)
    @PostMapping("/webhook")
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
        String result = paymentService.handleStripeEvent(payload, sigHeader);
        if (INVALID_SIGNATURE.equals(result)) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/payment-status/{intentId}", produces = "application/json")
    public Map<String, String> getPaymentStatus(@PathVariable String intentId) {
        return Map.of("status", paymentService.getStatus(intentId));
    }

    @PostMapping(path = "/cancel-payment/{intentId}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<String> cancelPaymentIntent(@PathVariable String intentId) {
        try {
            paymentService.cancelPayment(intentId);
            return ResponseEntity.ok("Canceled");
        } catch (StripeException e) {
            return ResponseEntity.badRequest().body("Cancellation Failed");
        }
    }

}
