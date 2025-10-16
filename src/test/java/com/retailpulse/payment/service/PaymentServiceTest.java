package com.retailpulse.payment.service;

import com.retailpulse.payment.events.PaymentCommittedEvent;
import com.retailpulse.payment.model.Payment;
import com.retailpulse.payment.model.PaymentStatus;
import com.retailpulse.payment.payloads.PaymentData;
import com.retailpulse.payment.payloads.PaymentResponse;
import com.retailpulse.payment.repos.PaymentRepo;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.retailpulse.payment.model.Constants.INVALID_SIGNATURE;
import static com.retailpulse.payment.model.Constants.STRIPE_PAYMENT_RECEIVED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepo paymentRepo;
    @Mock private ApplicationEventPublisher appEvents;

    @InjectMocks
    private PaymentService service;

    @BeforeEach
    void injectSecrets() {
        // match your @Value fields
        ReflectionTestUtils.setField(service, "stripeSecretKey", "sk_test_fake");
        ReflectionTestUtils.setField(service, "webhookEndpointKey", "whsec_fake");
    }

    @Test
    void createPaymentIntent_happyPath_persistsAndPublishes() throws Exception {
        // Arrange input
        PaymentData data = new PaymentData();
        data.setTransactionId(123L);
        data.setTotalPrice(15.00); // dollars
        data.setCurrency("SGD");
        data.setDescription("Coffee");
        data.setCustomerEmail("a@b.com");

        // Mock Stripe static: PaymentIntent.create(...)
        PaymentIntent mockPI = mock(PaymentIntent.class);
        when(mockPI.getId()).thenReturn("pi_123");
        when(mockPI.getClientSecret()).thenReturn("cs_abc");

        try (MockedStatic<PaymentIntent> mocked = Mockito.mockStatic(PaymentIntent.class)) {
            mocked.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class))).thenReturn(mockPI);

            // Mock DB save to assign id + createdDate
            when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(42L);
                var createdDate = Instant.parse("2025-10-03T00:00:00Z").atZone(ZoneOffset.UTC).toLocalDateTime();
                p.setCreatedDate(createdDate);
                return p;
            });

            // Act
            PaymentResponse resp = service.createPaymentIntent(data);

            // Assert response mapping
            assertThat(resp.getClientSecret()).isEqualTo("cs_abc");
            assertThat(resp.getPaymentIntentId()).isEqualTo("pi_123");
            assertThat(resp.getPaymentId()).isEqualTo(42L);
            assertThat(resp.getTransactionId()).isEqualTo(123L);
            assertThat(resp.getTotalPrice()).isEqualTo(15.00); // dollars back from cents/100
            assertThat(resp.getCurrency()).isEqualTo("SGD");
            assertThat(resp.getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);
            assertThat(resp.getPaymentDate()).isEqualTo(Instant.parse("2025-10-03T00:00:00Z").atZone(ZoneOffset.UTC).toLocalDateTime());

            // Verify persistence + event
            verify(paymentRepo).save(any(Payment.class));
            verify(appEvents).publishEvent(isA(PaymentCommittedEvent.class));
        }
    }

    @Test
    void handleStripeEvent_invalidSignature_returnsInvalid() {
        String payload = "{json}";
        String sig = "hdr";

        try (MockedStatic<Webhook> mocked = Mockito.mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(payload, sig, "whsec_fake"))
                    .thenThrow(new SignatureVerificationException("bad sig", null));

            String result = service.handleStripeEvent(payload, sig);

            assertThat(result).isEqualTo("Invalid signature");
        }
    }

    @Test
    void handleStripeEvent_succeeded_updatesStatusAndPublishes() {

        Event event = mock(Event.class, RETURNS_DEEP_STUBS);
        when(event.getType()).thenReturn("payment_intent.succeeded");

        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn("pi_123");

        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);
        when(deser.getObject()).thenReturn(Optional.of(pi));
        when(event.getDataObjectDeserializer()).thenReturn(deser);

        try (MockedStatic<Webhook> mocked = Mockito.mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_fake")))
                    .thenReturn(event);

            // Repo returns existing payment so updatePaymentStatus() can persist and publish
            Payment existing = new Payment();
            existing.setId(99L);
            existing.setPaymentIntentId("pi_123");
            existing.setPaymentStatus(PaymentStatus.PROCESSING);
            when(paymentRepo.findByPaymentIntentId("pi_123")).thenReturn(Optional.of(existing));
            when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            String result = service.handleStripeEvent("{payload}", "sig");

            // Assert
            assertThat(result).isEqualTo("received");

            ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepo).save(saved.capture());
            assertThat(saved.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

            verify(appEvents).publishEvent(isA(PaymentCommittedEvent.class));
        }
    }

    @Test
    void cancelPayment_callsStripeAndUpdatesStatus() throws Exception {
        PaymentIntent pi = mock(PaymentIntent.class);

        try (MockedStatic<PaymentIntent> mocked = Mockito.mockStatic(PaymentIntent.class)) {
            mocked.when(() -> PaymentIntent.retrieve("pi_123")).thenReturn(pi);

            // find + save path for updatePaymentStatus
            Payment p = new Payment();
            p.setId(1L);
            p.setPaymentIntentId("pi_123");
            p.setPaymentStatus(PaymentStatus.PROCESSING);
            when(paymentRepo.findByPaymentIntentId("pi_123")).thenReturn(Optional.of(p));
            when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            // Act
            service.cancelPayment("pi_123");

            // Assert Stripe cancel() called
            verify(pi).cancel();

            // Assert saved as CANCELED
            ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepo).save(saved.capture());
            assertThat(saved.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);

            verify(appEvents).publishEvent(isA(PaymentCommittedEvent.class));
        }
    }

    @Test
    void getStatus_returnsNotFoundWhenMissing() {
        when(paymentRepo.findByPaymentIntentId("nope")).thenReturn(Optional.empty());
        assertThat(service.getStatus("nope")).isEqualTo("NOT_FOUND");
    }
}
