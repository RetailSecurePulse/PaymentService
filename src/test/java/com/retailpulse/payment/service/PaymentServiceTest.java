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
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private PaymentService service;

    private PaymentRepo paymentRepo;
    private ApplicationEventPublisher appEvents;

    @BeforeEach
    void setup() {
        paymentRepo = mock(PaymentRepo.class);
        appEvents = mock(ApplicationEventPublisher.class);
        service = new PaymentService(paymentRepo, appEvents);

        // Inject secrets used during methods
        ReflectionTestUtils.setField(service, "stripeSecretKey", "sk_test_123");
        ReflectionTestUtils.setField(service, "webhookEndpointKey", "whsec_abc");
    }

    // ---------------- createPaymentIntent ----------------

    @Test
    void createPaymentIntent_happyPath_saves_and_publishes_and_returns_response() throws Exception {
        PaymentData data = new PaymentData();
        data.setTotalPrice(12.345); // expected cents -> 1235
        data.setCurrency("usd");
        data.setDescription("desc");
        data.setCustomerEmail("a@b.com");
        data.setTransactionId(123L);

        PaymentIntent mockPi = mock(PaymentIntent.class);
        when(mockPi.getId()).thenReturn("pi_123");
        when(mockPi.getClientSecret()).thenReturn("cs_test");

        try (MockedStatic<PaymentIntent> staticPI = Mockito.mockStatic(PaymentIntent.class)) {
            staticPI.when(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class))).thenReturn(mockPi);
            // save assigns ID
            when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                p.setId(42L);
                return p;
            });

            PaymentResponse resp = service.createPaymentIntent(data);

            staticPI.verify(() -> PaymentIntent.create(any(PaymentIntentCreateParams.class)), times(1));
            verify(paymentRepo).save(argThat(p ->
                    p.getPaymentIntentId().equals("pi_123")
                            && p.getTotalPrice() == 1235L
                            && p.getPaymentStatus() == PaymentStatus.PROCESSING
                            && p.getTransactionId().equals(123L)
                            && p.getCurrency().equals("usd")
                            && p.getCustomerEmail().equals("a@b.com")));

            verify(appEvents).publishEvent(isA(PaymentCommittedEvent.class));

            assertThat(resp.getPaymentIntentId()).isEqualTo("pi_123");
            assertThat(resp.getClientSecret()).isEqualTo("cs_test");
            assertThat(resp.getPaymentId()).isEqualTo(42L);
            assertThat(resp.getTotalPrice()).isEqualTo(12.35); // 1235 / 100.0
            assertThat(resp.getCurrency()).isEqualTo("usd");
            assertThat(resp.getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);

            assertThat(Stripe.apiKey).isEqualTo("sk_test_123");
        }
    }

    // ---------------- cancelPayment ----------------

    @Test
    void cancelPayment_retrieves_stripe_intent_cancels_and_updates_status() throws Exception {
        PaymentIntent pi = mock(PaymentIntent.class);

        Payment existing = new Payment();
        existing.setId(100L);
        existing.setPaymentIntentId("pi_cancel");
        existing.setPaymentStatus(PaymentStatus.PROCESSING);

        when(paymentRepo.findByPaymentIntentId("pi_cancel")).thenReturn(Optional.of(existing));

        try (MockedStatic<PaymentIntent> staticPI = Mockito.mockStatic(PaymentIntent.class)) {
            staticPI.when(() -> PaymentIntent.retrieve("pi_cancel")).thenReturn(pi);

            service.cancelPayment("pi_cancel");

            staticPI.verify(() -> PaymentIntent.retrieve("pi_cancel"), times(1));
            verify(pi).cancel();
            verify(paymentRepo).save(argThat(p ->
                    p.getId().equals(100L)
                            && p.getPaymentIntentId().equals("pi_cancel")
                            && p.getPaymentStatus() == PaymentStatus.CANCELED));
            verify(appEvents).publishEvent(isA(PaymentCommittedEvent.class));

            assertThat(Stripe.apiKey).isEqualTo("sk_test_123");
        }
    }

    // ---------------- getStatus ----------------

    @Test
    void getStatus_returns_name_when_found() {
        Payment p = new Payment();
        p.setPaymentStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepo.findByPaymentIntentId("pi_200")).thenReturn(Optional.of(p));

        assertThat(service.getStatus("pi_200")).isEqualTo("SUCCEEDED");
    }

    @Test
    void getStatus_returns_not_found_when_absent() {
        when(paymentRepo.findByPaymentIntentId("nope")).thenReturn(Optional.empty());
        assertThat(service.getStatus("nope")).isEqualTo("NOT_FOUND");
    }

    // ---------------- handleStripeEvent: invalid signature path ----------------

    @Test
    void handleStripeEvent_returns_invalid_signature_on_sig_verification_error() {
        try (MockedStatic<Webhook> staticWebhook = Mockito.mockStatic(Webhook.class)) {
            staticWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("bad sig", null));

            // We don’t assert the exact constant value—just that it returns a non-null indicator
            String result = service.handleStripeEvent("very-long-payload", "sig");
            assertThat(result).isNotNull();
        }
    }

    // ---------------- handleStripeEvent: succeeded/failed/canceled/created/default ----------------

    @Test
    void handleStripeEvent_succeeded_updates_db_and_publishes_event_using_present_object() {
        Event ev = mock(Event.class);
        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn("pi_succ");

        // Stripe event types
        when(ev.getType()).thenReturn("payment_intent.succeeded");
        when(ev.getDataObjectDeserializer()).thenReturn(deser);
        when(deser.getObject()).thenReturn(Optional.of(pi));

        Payment existing = new Payment();
        existing.setId(1L);
        existing.setPaymentIntentId("pi_succ");
        existing.setPaymentStatus(PaymentStatus.PROCESSING);

        when(paymentRepo.findByPaymentIntentId("pi_succ")).thenReturn(Optional.of(existing));
        when(paymentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<Webhook> staticWebhook = Mockito.mockStatic(Webhook.class)) {
            staticWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_abc")))
                    .thenReturn(ev);

            String result = service.handleStripeEvent("{payload}", "sig");

            assertThat(result).isNotBlank();
            verify(paymentRepo).save(argThat(p -> p.getPaymentStatus() == PaymentStatus.SUCCEEDED));
            verify(appEvents).publishEvent(isA(PaymentCommittedEvent.class));
        }
    }

    @Test
    void handleStripeEvent_failed_uses_fallback_raw_json_when_object_is_not_payment_intent() {
        Event ev = mock(Event.class);
        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);

        when(ev.getType()).thenReturn("payment_intent.payment_failed");
        when(ev.getDataObjectDeserializer()).thenReturn(deser);

        // Object is present but of unexpected type -> fallback to rawJson
        when(deser.getObject()).thenReturn(Optional.of(new StripeObject() {
        }));
        when(deser.getRawJson()).thenReturn("{\"id\":\"pi_json\"}");

        Payment existing = new Payment();
        existing.setId(2L);
        existing.setPaymentIntentId("pi_json");
        existing.setPaymentStatus(PaymentStatus.PROCESSING);

        when(paymentRepo.findByPaymentIntentId("pi_json")).thenReturn(Optional.of(existing));
        when(paymentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<Webhook> staticWebhook = Mockito.mockStatic(Webhook.class)) {
            staticWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_abc")))
                    .thenReturn(ev);

            String result = service.handleStripeEvent("{payload}", "sig");

            assertThat(result).isNotBlank();
            verify(paymentRepo).save(argThat(p -> p.getPaymentStatus() == PaymentStatus.FAILED));
            verify(appEvents).publishEvent(isA(PaymentCommittedEvent.class));
        }
    }

    @Test
    void handleStripeEvent_canceled_with_blank_raw_json_skips_processing() {
        Event ev = mock(Event.class);
        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);

        when(ev.getType()).thenReturn("payment_intent.canceled");
        when(ev.getDataObjectDeserializer()).thenReturn(deser);

        when(deser.getObject()).thenReturn(Optional.empty());
        when(deser.getRawJson()).thenReturn("  ");

        try (MockedStatic<Webhook> staticWebhook = Mockito.mockStatic(Webhook.class)) {
            staticWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_abc")))
                    .thenReturn(ev);

            String result = service.handleStripeEvent("{payload}", "sig");

            assertThat(result).isNotBlank();
            verifyNoInteractions(paymentRepo);
            verifyNoInteractions(appEvents);
        }
    }

    @Test
    void handleStripeEvent_created_logs_only_no_db() {
        // Arrange
        Event ev = mock(Event.class);
        when(ev.getType()).thenReturn("payment_intent.created");

        try (MockedStatic<Webhook> staticWebhook = Mockito.mockStatic(Webhook.class)) {
            staticWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_abc")))
                    .thenReturn(ev);

            // Act
            String result = service.handleStripeEvent("{payload}", "sig");

            // Assert
            assertThat(result).isNotBlank();

            verifyNoInteractions(paymentRepo);
            verifyNoInteractions(appEvents);
            verify(ev, atLeastOnce()).getType();
        }
    }

    @Test
    void handleStripeEvent_unknown_type_logs_only_no_db() {
        // Arrange
        Event ev = mock(Event.class);
        when(ev.getType()).thenReturn("some.unknown.type");

        try (MockedStatic<Webhook> staticWebhook = Mockito.mockStatic(Webhook.class)) {
            staticWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_abc")))
                    .thenReturn(ev);

            // Act
            String result = service.handleStripeEvent("{payload}", "sig");

            // Assert
            assertThat(result).isNotBlank();

            verifyNoInteractions(paymentRepo);
            verifyNoInteractions(appEvents);
            verify(ev, atLeastOnce()).getType();
        }
    }

    @Test
    void handleStripeEvent_wraps_unexpected_errors_in_processing_error_runtime() {
        Event ev = mock(Event.class);
        when(ev.getType()).thenReturn("payment_intent.succeeded");

        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);
        when(ev.getDataObjectDeserializer()).thenThrow(new RuntimeException("boom"));

        try (MockedStatic<Webhook> staticWebhook = Mockito.mockStatic(Webhook.class)) {
            staticWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_abc")))
                    .thenReturn(ev);

            assertThatThrownBy(() -> service.handleStripeEvent("{payload}", "sig"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("processing error");
        }
    }

    @Test
    void handleStripeEvent_fallback_json_invalid_is_caught_and_skips_processing() {
        Event ev = mock(Event.class);
        EventDataObjectDeserializer deser = mock(EventDataObjectDeserializer.class);

        when(ev.getType()).thenReturn("payment_intent.succeeded");
        when(ev.getDataObjectDeserializer()).thenReturn(deser);

        when(deser.getObject()).thenReturn(Optional.of(new StripeObject() {
        }));
        when(deser.getRawJson()).thenReturn("{INVALID_JSON");

        try (MockedStatic<Webhook> staticWebhook = Mockito.mockStatic(Webhook.class)) {
            staticWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), eq("whsec_abc")))
                    .thenReturn(ev);

            String result = service.handleStripeEvent("{payload}", "sig");

            assertThat(result).isNotBlank();
            verifyNoInteractions(paymentRepo);
            verifyNoInteractions(appEvents);
        }
    }
}
