package com.retailpulse.payment.events;

import com.retailpulse.payment.repos.PaymentRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentOutHandler {

    private final PaymentEventPublisher publisher;
    private final PaymentRepo paymentRepo;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentOutMessage(PaymentCommittedEvent event) {
        paymentRepo.findById(event.id()).ifPresent( payment -> {
            switch (event.status()) {
                case PROCESSING -> publisher.publishPaymentProcessingEvent(payment);
                case SUCCEEDED -> publisher.publishPaymentSucceededEvent(payment);
                case FAILED -> publisher.publishPaymentFailedEvent(payment);
                case CANCELED -> publisher.publishPaymentCanceledEvent(payment);
            }
        });

    }
}
