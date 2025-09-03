package com.retailpulse.payment.service;

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
import org.springframework.stereotype.Service;

import static com.retailpulse.payment.model.Constants.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String STRIPE_PUBLIC_KEY = "pk_test_51Rwa9JCTUDg2faMiUxYG28Di0rDMjD4C5xEPCkn0nv6bPc1Qy8WvivfAhhykVxGlAqfeF2tvILpEd0K9je6WPhLo00bfZAazGS";
    private static final String STRIPE_SECRET_KEY = "sk_test_51Rwa9JCTUDg2faMia0WxH3YMsOxisCmZ9OsuL6Lcl5OF17dkOFF7smfZeBYXWaOxWnIyTeQHvufuKjFQsyS36eVj00cc8enDMQ";
    private static final String WEBHOOK_ENDPOINT_KEY = "whsec_8e7a9359be842a9398f1d48ae97e4c7a77d555093fe0a9466b6ca699045b0a77";

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepo paymentRepo;

    @Transactional
    public PaymentResponse createPaymentIntent(PaymentData data) throws StripeException {
        Stripe.apiKey = STRIPE_SECRET_KEY;
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
        paymentRepo.save(payment);
        return new PaymentResponse(paymentIntent.getClientSecret(), paymentIntent.getId());
    }

    @Transactional
    public String handleStripeEvent(String payload, String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, WEBHOOK_ENDPOINT_KEY);
            switch (event.getType()) {
                case PAYMENT_SUCCESS_WEBHOOK -> {
                    PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElseThrow();
                    updatePaymentStatus(intent.getId(), PaymentStatus.SUCCEEDED);
                    logger.info("Payment succeeded for PaymentIntent ID: {}", intent.getId());
                    logger.info("Payment succeeded event type: {}", event.getType());
                }
                case PAYMENT_FAILED_WEBHOOK -> {
                    PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElseThrow();
                    updatePaymentStatus(intent.getId(), PaymentStatus.FAILED);
                    logger.info("Payment failed for PaymentIntent ID: {}", intent.getId());
                    logger.info("Payment failed event type: {}", event.getType());
                }
                case PAYMENT_CANCELED_WEBHOOK -> {
                    PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElseThrow();
                    updatePaymentStatus(intent.getId(), PaymentStatus.CANCELED);
                    logger.warn("Payment canceled for PaymentIntent ID: {}", intent.getId());
                    logger.info("Payment canceled event type: {}", event.getType());
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
