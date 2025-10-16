package com.retailpulse.payment.events;

import com.retailpulse.payment.model.Payment;
import com.retailpulse.payment.model.PaymentStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentEventPublisherTest {

    @Mock
    KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @InjectMocks
    PaymentEventPublisher publisher;

    private static final String TOPIC = "payment-events";
    private static final String DLT = "payment-events-dlt";

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(publisher, "topic", TOPIC);
        ReflectionTestUtils.setField(publisher, "dlTopic", DLT);
    }

    // ---------- happy paths (mapping + send) ----------

    @Test
    void publishProcessing_mapsAllFields_andSends() {
        Payment payment = stubPayment();

        when(kafkaTemplate.send(eq(TOPIC), eq("pi_123"), any(PaymentEvent.class)))
                .thenReturn(completedSend(TOPIC, 0, 10L, "pi_123"));

        publisher.publishPaymentProcessingEvent(payment);

        PaymentEvent ev = captureEventSentTo(TOPIC, "pi_123");
        assertThat(ev.getPaymentStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertCommonMapping(ev);
    }

    @Test
    void publishSucceeded_mapsAllFields_andSends() {
        Payment payment = stubPayment();

        when(kafkaTemplate.send(eq(TOPIC), eq("pi_123"), any(PaymentEvent.class)))
                .thenReturn(completedSend(TOPIC, 1, 11L, "pi_123"));

        publisher.publishPaymentSucceededEvent(payment);

        PaymentEvent ev = captureEventSentTo(TOPIC, "pi_123");
        assertThat(ev.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertCommonMapping(ev);
    }

    @Test
    void publishFailed_mapsAllFields_andSends() {
        Payment payment = stubPayment();

        when(kafkaTemplate.send(eq(TOPIC), eq("pi_123"), any(PaymentEvent.class)))
                .thenReturn(completedSend(TOPIC, 2, 12L, "pi_123"));

        publisher.publishPaymentFailedEvent(payment);

        PaymentEvent ev = captureEventSentTo(TOPIC, "pi_123");
        assertThat(ev.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertCommonMapping(ev);
    }

    @Test
    void publishCanceled_mapsAllFields_andSends() {
        Payment payment = stubPayment();

        when(kafkaTemplate.send(eq(TOPIC), eq("pi_123"), any(PaymentEvent.class)))
                .thenReturn(completedSend(TOPIC, 3, 13L, "pi_123"));

        publisher.publishPaymentCanceledEvent(payment);

        PaymentEvent ev = captureEventSentTo(TOPIC, "pi_123");
        assertThat(ev.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertCommonMapping(ev);
    }

    // ---------- DLT behaviour ----------

    @Test
    void publishesToDlt_whenPrimarySendFails() {
        Payment payment = stubPayment();

        when(kafkaTemplate.send(eq(TOPIC), eq("pi_123"), any(PaymentEvent.class)))
                .thenReturn(failedSend(new RuntimeException("broker down")));
        when(kafkaTemplate.send(eq(DLT), eq("pi_123"), any(PaymentEvent.class)))
                .thenReturn(completedSend(DLT, 7, 77L, "pi_123"));

        publisher.publishPaymentFailedEvent(payment);

        // verify both sends, and that the same (mapped) event went to DLT
        verify(kafkaTemplate).send(eq(TOPIC), eq("pi_123"), any(PaymentEvent.class));
        PaymentEvent evToDlt = captureEventSentTo(DLT, "pi_123");
        assertThat(evToDlt.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertCommonMapping(evToDlt);
    }

    @Test
    void stillAttemptsDlt_andSwallows_whenBothPrimaryAndDltFail() {
        Payment payment = stubPayment();

        when(kafkaTemplate.send(eq(TOPIC), eq("pi_123"), any(PaymentEvent.class)))
                .thenReturn(failedSend(new RuntimeException("primary fail")));
        when(kafkaTemplate.send(eq(DLT), eq("pi_123"), any(PaymentEvent.class)))
                .thenReturn(failedSend(new RuntimeException("dlt fail")));

        publisher.publishPaymentSucceededEvent(payment);

        verify(kafkaTemplate).send(eq(TOPIC), eq("pi_123"), any(PaymentEvent.class));
        verify(kafkaTemplate).send(eq(DLT), eq("pi_123"), any(PaymentEvent.class));
    }

    // ---------- helpers ----------

    private Payment stubPayment() {
        Payment p = mock(Payment.class);
        when(p.getId()).thenReturn(42L);
        when(p.getPaymentIntentId()).thenReturn("pi_123");
        when(p.getTransactionId()).thenReturn(123L);
        when(p.getTotalPrice()).thenReturn(12345L);
        when(p.getCurrency()).thenReturn("USD");
        when(p.getCustomerEmail()).thenReturn("alice@example.com");
        when(p.getModifiedDate()).thenReturn(Instant.parse("2025-10-10T10:15:30Z").atZone(ZoneOffset.UTC).toLocalDateTime());
        return p;
    }

    private void assertCommonMapping(PaymentEvent ev) {
        assertThat(ev.getPaymentId()).isEqualTo(42L);
        assertThat(ev.getPaymentIntentId()).isEqualTo("pi_123");
        assertThat(ev.getTransactionId()).isEqualTo(123L);
        assertThat(ev.getTotalPrice()).isEqualByComparingTo(12345L);
        assertThat(ev.getCurrency()).isEqualTo("USD");
        assertThat(ev.getCustomerEmail()).isEqualTo("alice@example.com");
        assertThat(ev.getPaymentDate()).isEqualTo(Instant.parse("2025-10-10T10:15:30Z").atZone(ZoneOffset.UTC).toLocalDateTime());
    }

    private PaymentEvent captureEventSentTo(String topic, String key) {
        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(kafkaTemplate).send(eq(topic), eq(key), captor.capture());
        return captor.getValue();
    }

    private static CompletableFuture<SendResult<String, PaymentEvent>> completedSend(
            String topic, int partition, long offset, String key) {

        ProducerRecord<String, PaymentEvent> pr = new ProducerRecord<>(topic, key, null);

        RecordMetadata md = new RecordMetadata(
                new TopicPartition(topic, partition),
                offset,
                0,
                System.currentTimeMillis(),
                0,
                0
        );

        SendResult<String, PaymentEvent> result = new SendResult<>(pr, md);
        return CompletableFuture.completedFuture(result);
    }

    private static CompletableFuture<SendResult<String, PaymentEvent>> failedSend(Throwable t) {
        CompletableFuture<SendResult<String, PaymentEvent>> cf = new CompletableFuture<>();
        cf.completeExceptionally(t);
        return cf;
    }

}
