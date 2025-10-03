package com.retailpulse.payment.events;

import com.retailpulse.payment.model.Payment;
import com.retailpulse.payment.model.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Value("${app.kafka.topics.payment-events:payment-events}")
    private String topic;
    @Value("${app.kafka.topics.payment-events-dlt:payment-events-dlt}")
    private String dlTopic;


    private PaymentEvent createPaymentEvent(Payment payment, PaymentStatus status) {
        return PaymentEvent.builder()
                .paymentId(payment.getId())
                .paymentIntentId(payment.getPaymentIntentId())
                .transactionId(payment.getTransactionId())
                .paymentStatus(status)
                .totalPrice(payment.getTotalPrice())
                .currency(payment.getCurrency())
                .customerEmail(payment.getCustomerEmail())
                .paymentDate(payment.getModifiedDate())
                .build();
    }

    public void publishPaymentProcessingEvent(Payment payment) {
        sendWithDlt(createPaymentEvent(payment, PaymentStatus.PROCESSING), payment.getPaymentIntentId());
    }

    public void publishPaymentSucceededEvent(Payment payment) {
        sendWithDlt(createPaymentEvent(payment, PaymentStatus.SUCCEEDED), payment.getPaymentIntentId());
    }

    public void publishPaymentFailedEvent(Payment payment) {
        sendWithDlt(createPaymentEvent(payment, PaymentStatus.FAILED), payment.getPaymentIntentId());
    }

    public void publishPaymentCanceledEvent(Payment payment) {
        sendWithDlt(createPaymentEvent(payment, PaymentStatus.CANCELED), payment.getPaymentIntentId());
    }

    private void sendWithDlt(PaymentEvent event, String paymentIntentId) {
        kafkaTemplate.send(topic, paymentIntentId, event).whenComplete((meta, exception) -> {
            if (exception != null) {
                log.error("Kafka publish failed, sending to DLT. event: {}, PaymentIntentId: {}, topic: {}", event.getPaymentStatus(), paymentIntentId, topic, exception);
                kafkaTemplate.send(dlTopic, paymentIntentId, event).whenComplete((dltMeta, dltException) -> {
                    if (dltException != null) {
                        log.error("Kafka DLT publish Payment also failed. event: {}, PaymentIntentId: {}, topic: {}", event.getPaymentStatus(), paymentIntentId, dlTopic, dltException);
                    } else {
                        log.info("Kafka publish to DLT succeeded. event: {}, PaymentIntentId: {}, topic: {}, partition: {}, offset: {}",
                                event.getPaymentStatus(), paymentIntentId, dltMeta.getRecordMetadata().topic(), dltMeta.getRecordMetadata().partition(), dltMeta.getRecordMetadata().offset());
                    }
                });
            } else {
                log.info("Kafka publish Payment event succeeded. event: {}, PaymentIntentId: {}, topic: {}, partition: {}, offset: {}",
                        event.getPaymentStatus(), paymentIntentId, meta.getRecordMetadata().topic(), meta.getRecordMetadata().partition(), meta.getRecordMetadata().offset());
            }
        });
    }
}
