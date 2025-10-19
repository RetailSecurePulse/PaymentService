package com.retailpulse.payment.service;

import com.retailpulse.payment.events.PaymentCommittedEvent;
import com.retailpulse.payment.model.Payment;
import com.retailpulse.payment.model.PaymentStatus;
import com.retailpulse.payment.payloads.PaymentData;
import com.retailpulse.payment.payloads.PaymentResponse;
import com.retailpulse.payment.repos.PaymentRepo;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepo paymentRepo;
    @Mock private ApplicationEventPublisher appEvents;

    @InjectMocks private PaymentService service;

    @BeforeEach
    void init() {
        // Set @Value fields
        ReflectionTestUtils.setField(service, "stripeSecretKey", "sk_test_123");
        ReflectionTestUtils.setField(service, "webhookEndpointKey", "whsec_123");
    }

    // ---------- createPaymentIntent ----------

    @Test
    void createPaymentIntent_happyPath_savesAndPublishes_andReturnsResponse() throws Exception {
        // Arrange
        PaymentData data = new PaymentData();
        data.setTotalPrice(12.345); // will be rounded HALF_UP to 1235 cents
        data.setCurrency("usd");
        data.setDescription("desc");
        data.setCustomerEmail("a@b.com");
        data.setTransactionId(123L);

        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn("pi_123");
        when(pi.getClientSecret()).thenReturn("cs_test");

        try (MockedStatic<PaymentIntent> staticPI = Mockito.mockStatic(PaymentIntent.class)) {
            staticPI.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

            when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(42L);
                return p;
            });

            // Act
            PaymentResponse resp = service.createPaymentIntent(data);

            // Assert
            staticPI.verify(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)), times(1));
            verify(paymentRepo).save(argThat(p ->
                    p.getPaymentIntentId().equals("pi_123")
                            && p.getTotalPrice() == 1235L
                            && p.getPaymentStatus() == PaymentStatus.PROCESSING
                            && p.getTransactionId().equals(123L)
                            && p.getCurrency().equals("usd")
                            && p.getCustomerEmail().equals("a@b.com")));

            verify(appEvents).publishEvent(isA(PaymentCommittedEvent.class));

            assertThat(resp).isNotNull();
            assertThat(resp.getPaymentIntentId()).isEqualTo("pi_123");
            assertThat(resp.getClientSecret()).isEqualTo("cs_test");
            assertThat(resp.getPaymentId()).isEqualTo(42L);
            assertThat(resp.getTotalPrice()).isEqualTo(12.35); // 1235 / 100.0
            assertThat(resp.getCurrency()).isEqualTo("usd");
            assertThat(resp.getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);

            // sanity: service set Stripe.apiKey
            assertThat(Stripe.apiKey).isEqualTo("sk_test_123");
        }
    }

    // ---------- cancelPayment ----------

    @Test
    void cancelPayment_callsStripeAndUpdatesStatusAndPublishesEvent() throws Exception {
        PaymentIntent pi = mock(PaymentIntent.class);

        try (MockedStatic<PaymentIntent> staticPI = Mockito.mockStatic(PaymentIntent.class)) {
            staticPI.when(() -> PaymentIntent.retrieve("pi_cancel")).thenReturn(pi);

            Payment existing = new Payment();
            existing.setId(100L);
            existing.setPaymentIntentId("pi_cancel");
            existing.setPaymentStatus(PaymentStatus.PROCESSING);

            when(paymentRepo.findByPaymentIntentId("pi_cancel")).thenReturn(Optional.of(existing));
            when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            service.cancelPayment("pi_cancel");

            staticPI.verify(() -> PaymentIntent.retrieve("pi_cancel"), times(1));
            verify(pi).cancel();

            verify(paymentRepo).findByPaymentIntentId("pi_cancel");
            verify(paymentRepo).save(argThat(p ->
                    p.getId().equals(100L)
                            && p.getPaymentIntentId().equals("pi_cancel")
                            && p.getPaymentStatus() == PaymentStatus.CANCELED
            ));
            verify(appEvents).publishEvent(isA(PaymentCommittedEvent.class));

            assertThat(Stripe.apiKey).isEqualTo("sk_test_123");
        }
    }

    // ---------- getStatus ----------

    @Test
    void getStatus_returnsFoundStatus() {
        Payment p = new Payment();
        p.setPaymentStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepo.findByPaymentIntentId("pi_ok")).thenReturn(Optional.of(p));

        assertThat(service.getStatus("pi_ok")).isEqualTo("SUCCEEDED");
    }

    @Test
    void getStatus_returnsNotFound() {
        when(paymentRepo.findByPaymentIntentId("pi_missing")).thenReturn(Optional.empty());
        assertThat(service.getStatus("pi_missing")).isEqualTo("NOT_FOUND");
    }

    // ---------- handleStripeEvent: invalid signature ----------

    @Test
    void handleStripeEvent_invalidSignature_returnsConstant() {
        try (MockedStatic<Webhook> staticWebhook = Mockito.mockStatic(Webhook.class)) {
            staticWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("bad", "sig"));

            String result = service.handleStripeEvent("{payload}", "sig");
            assertThat(result).isEqualTo("Invalid signature");
        }
    }

    // ---------- handleStripeEvent: default/unhandled type returns received ----------

    @Test
    void handleStripeEvent_unhandledType_returnsReceived() {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("some_unknown_type");

        try (MockedStatic<Webhook> staticWebhook = Mockito.mockStatic(Webhook.class)) {
            staticWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            String result = service.handleStripeEvent("{payload}", "sig");
            assertThat(result).isEqualTo("received");
        }
    }

    // ---------- handlePaymentIntentEvent (private) via reflection ----------

    @Test
    void handlePaymentIntentEvent_withPresentPaymentIntent_updatesStatusAndPublishes() throws Exception {
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn("pi_789");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(pi));

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_1");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        Payment existing = new Payment();
        existing.setId(7L);
        existing.setPaymentIntentId("pi_789");
        existing.setPaymentStatus(PaymentStatus.PROCESSING);
        when(paymentRepo.findByPaymentIntentId("pi_789")).thenReturn(Optional.of(existing));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        var m = PaymentService.class.getDeclaredMethod("handlePaymentIntentEvent", Event.class, PaymentStatus.class);
        m.setAccessible(true);
        m.invoke(service, event, PaymentStatus.SUCCEEDED);

        verify(paymentRepo).save(argThat(p ->
                p.getId().equals(7L)
                        && p.getPaymentIntentId().equals("pi_789")
                        && p.getPaymentStatus() == PaymentStatus.SUCCEEDED
        ));
        verify(appEvents).publishEvent(isA(PaymentCommittedEvent.class));
    }

    @Test
    void handlePaymentIntentEvent_noObjectAndNoRawJson_skipsUpdate() throws Exception {
        // deserializer returns empty object and blank raw JSON -> extractPaymentIntent => null
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.empty());
        when(deserializer.getRawJson()).thenReturn("  ");

        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_2");
        when(event.getType()).thenReturn("payment_intent.succeeded");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        // Invoke private method
        var m = PaymentService.class.getDeclaredMethod("handlePaymentIntentEvent", Event.class, PaymentStatus.class);
        m.setAccessible(true);
        m.invoke(service, event, PaymentStatus.SUCCEEDED);

        // Verify nothing updated/published
        verify(paymentRepo, never()).findByPaymentIntentId(anyString());
        verify(appEvents, never()).publishEvent(any());
    }

}
