package com.retailpulse.payment.service;

import com.google.gson.JsonSyntaxException;
import com.retailpulse.payment.events.PaymentCommittedEvent;
import com.retailpulse.payment.model.Payment;
import com.retailpulse.payment.model.PaymentStatus;
import com.retailpulse.payment.payloads.PaymentData;
import com.retailpulse.payment.payloads.PaymentResponse;
import com.retailpulse.payment.repos.PaymentRepo;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.retailpulse.payment.model.Constants.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    @Value("${stripe.apiKey}")
    private String stripeSecretKey;

    @Value("${stripe.webhookSecret}")
    private String webhookEndpointKey;

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepo paymentRepo;
    private final ApplicationEventPublisher appEvents;

    @Transactional
    public PaymentResponse createPaymentIntent(PaymentData data) throws StripeException {
        Stripe.apiKey = stripeSecretKey;
        long amountInCents = toCents(data.getTotalPrice());
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents) // Amount in cents
                .setCurrency(data.getCurrency())
                .setDescription(data.getDescription())
                .setReceiptEmail(data.getCustomerEmail())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                )
                .build();
        PaymentIntent paymentIntent = PaymentIntent.create(params);

        Payment payment = new Payment();
        payment.setTransactionId(data.getTransactionId());
        payment.setPaymentIntentId(paymentIntent.getId());
        payment.setTotalPrice(amountInCents);
        payment.setCurrency(data.getCurrency());
        payment.setPaymentStatus(PaymentStatus.PROCESSING);
        payment.setCustomerEmail(data.getCustomerEmail());
        payment = paymentRepo.save(payment);

        appEvents.publishEvent(new PaymentCommittedEvent(payment.getId(), paymentIntent.getId(), PaymentStatus.PROCESSING));
        logger.info("Created PaymentIntent with ID: {}", payment.getId());
        return new PaymentResponse(
                paymentIntent.getClientSecret(),
                paymentIntent.getId(),
                payment.getId(),
                payment.getTransactionId(),
                amountInCents / 100.0,
                payment.getCurrency(),
                payment.getPaymentStatus(),
                payment.getCreatedDate()
        );
    }

    private long toCents(Double amount) {
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    @Transactional
    public String handleStripeEvent(String payload, String sigHeader) {
        final Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookEndpointKey);
        } catch (SignatureVerificationException e) {
            logger.error("Webhook signature verification failed for payload: {}. Error: {}", safePreview(payload), e.getMessage(), e);
            return INVALID_SIGNATURE;
        }
        try {
            switch (event.getType()) {
                case PAYMENT_SUCCESS_WEBHOOK -> handlePaymentIntentEvent(event, PaymentStatus.SUCCEEDED);
                case PAYMENT_FAILED_WEBHOOK -> handlePaymentIntentEvent(event, PaymentStatus.FAILED);
                case PAYMENT_CANCELED_WEBHOOK -> handlePaymentIntentEvent(event, PaymentStatus.CANCELED);
                case PAYMENT_INTENT_CREATED_WEBHOOK -> logger.info("Payment intent created event type: {}", event.getType());
                default -> logger.info("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            logger.error("Failed to parse webhook payload. payloadPreview={}, error={}", safePreview(payload), e.getMessage(), e);
            throw new RuntimeException("processing error", e);
        }

        return STRIPE_PAYMENT_RECEIVED;
    }

    @Transactional
    public void cancelPayment(String intentId) throws StripeException {
        Stripe.apiKey = stripeSecretKey;
        PaymentIntent intent = PaymentIntent.retrieve(intentId);
        intent.cancel();
        updatePaymentStatus(intentId, PaymentStatus.CANCELED);
    }

    public String getStatus(String intentId) {
        return paymentRepo.findByPaymentIntentId(intentId)
                .map(payment -> payment.getPaymentStatus().name())
                .orElse("NOT_FOUND");
    }

    private void updatePaymentStatus(String intentId, PaymentStatus status) {
        paymentRepo.findByPaymentIntentId(intentId).ifPresent( payment -> {
            payment.setPaymentStatus(status);
            paymentRepo.save(payment);
            appEvents.publishEvent(new PaymentCommittedEvent(payment.getId(), payment.getPaymentIntentId(), payment.getPaymentStatus()));
        });
    }

    /**
     * Extracts a PaymentIntent from the event safely then delegates to a transactional processor.
     */
    private void handlePaymentIntentEvent(Event event, PaymentStatus targetStatus) {
        PaymentIntent intent = extractPaymentIntent(event);

        if (intent == null) {
            logger.error("Could not obtain PaymentIntent for event {} type {} — skipping processing",
                    event.getId(), event.getType());
            return;
        }
        updatePaymentStatus(intent.getId(), targetStatus);
        logger.info("Payment {} for PaymentIntent ID={} (event {})", targetStatus, intent.getId(), event.getId());
    }

    /**
     * Safely obtain PaymentIntent: try SDK deserializer, then raw JSON.
     */
    private PaymentIntent extractPaymentIntent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        var currentObject = deserializer.getObject();
        if (currentObject.isPresent()) {
            Object obj = currentObject.get();
            if (obj instanceof PaymentIntent pi) {
                return pi;
            }
            logger.warn("Event {}: data object is of type {} (expected PaymentIntent)",
                    event.getId(), obj.getClass().getName());
        }

        String rawJson = deserializer.getRawJson();
        if (rawJson == null || rawJson.isBlank()) {
            logger.error("Event {}: deserializer returned no raw JSON to attempt fallback deserialization", event.getId());
            return null;
        }
        try {
            PaymentIntent pi = ApiResource.GSON.fromJson(rawJson, PaymentIntent.class);
            if (pi != null) {
                logger.info("Deserialized PaymentIntent from raw JSON for event {} -> id={}", event.getId(), pi.getId());
                return pi;
            }
        } catch (JsonSyntaxException je) {
            logger.error("GSON failed to deserialize PaymentIntent for event {}. Raw payload preview={}. Error={}",
                    event.getId(), safePreview(rawJson), je.getMessage(), je);
        } catch (RuntimeException ex) {
            logger.error("Unexpected error deserializing PaymentIntent for event {}: {}", event.getId(), ex.getMessage(), ex);
        }

        return null;
    }

    /**
     * Helper to avoid logging giant payloads — returns short preview.
     */
    private String safePreview(String payload) {
        if (payload == null) return "<null>";
        return payload.length() <= 200 ? payload : (payload.substring(0, 200) + "...(truncated)");
    }

}
