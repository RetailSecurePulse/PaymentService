package com.retailpulse.payment.service;

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
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

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
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount((long) (data.getTotalPrice() * 100)) // Amount in cents
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
        payment.setTotalPrice((long) (data.getTotalPrice() * 100));
        payment.setCurrency(data.getCurrency());
        payment.setPaymentStatus(PaymentStatus.PROCESSING);
        payment.setCustomerEmail(data.getCustomerEmail());
        payment = paymentRepo.save(payment);

        appEvents.publishEvent(new PaymentCommittedEvent(payment.getId(), paymentIntent.getId(), PaymentStatus.PROCESSING));
        logger.info("Created PaymentIntent with ID: {}", payment.getId());
        return new PaymentResponse(paymentIntent.getClientSecret(), paymentIntent.getId());
    }

    @Transactional
    public String handleStripeEvent(String payload, String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookEndpointKey);
            PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElseThrow();
            switch (event.getType()) {
                case PAYMENT_SUCCESS_WEBHOOK -> {
                    updatePaymentStatus(intent.getId(), PaymentStatus.SUCCEEDED);
                    logger.info("Payment succeeded for PaymentIntent ID: {}, event: {}", intent.getId(), event.getType());
                    paymentRepo.findByPaymentIntentId(intent.getId()).ifPresent( p ->
                            appEvents.publishEvent(new PaymentCommittedEvent(p.getId(), p.getPaymentIntentId(), p.getPaymentStatus()))
                    );
                }
                case PAYMENT_FAILED_WEBHOOK -> {
                    updatePaymentStatus(intent.getId(), PaymentStatus.FAILED);
                    logger.info("Payment failed for PaymentIntent ID: {}, event: {}", intent.getId(), event.getType());
                    paymentRepo.findByPaymentIntentId(intent.getId()).ifPresent( p ->
                            appEvents.publishEvent(new PaymentCommittedEvent(p.getId(), p.getPaymentIntentId(), p.getPaymentStatus()))
                    );
                }
                case PAYMENT_CANCELED_WEBHOOK -> {
                    updatePaymentStatus(intent.getId(), PaymentStatus.CANCELED);
                    logger.warn("Payment canceled for PaymentIntent ID: {}, event: {}", intent.getId(), event.getType());
                    paymentRepo.findByPaymentIntentId(intent.getId()).ifPresent( p ->
                            appEvents.publishEvent(new PaymentCommittedEvent(p.getId(), p.getPaymentIntentId(), p.getPaymentStatus()))
                    );
                }
                case PAYMENT_INTENT_CREATED_WEBHOOK -> logger.info("Payment intent created event type: {}", event.getType());
                default -> logger.info("Unhandled event type: {}", event.getType());
            }

        } catch (SignatureVerificationException e) {
            logger.error("Webhook signature verification failed : {}", e.getMessage());
            return INVALID_SIGNATURE;
        }

        return STRIPE_PAYMENT_RECEIVED;
    }

    @Transactional
    public void cancelPayment(String intentId) throws StripeException {
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
        paymentRepo.findByPaymentIntentId(intentId)
                .ifPresent( payment -> {
                    payment.setPaymentStatus(status);
                    paymentRepo.save(payment);
                });
    }
}
