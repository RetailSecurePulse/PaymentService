package com.retailpulse.payment.events;

import com.retailpulse.payment.model.Payment;
import com.retailpulse.payment.model.PaymentStatus;
import com.retailpulse.payment.repos.PaymentRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.Mockito.*;

public class PaymentOutHandlerTest {
    @Mock
    PaymentEventPublisher publisher;

    @Mock
    PaymentRepo paymentRepo;

    @InjectMocks
    PaymentOutHandler handler;

    Payment payment;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        payment = new Payment();
        payment.setId(7L);
        payment.setPaymentIntentId("pi_abc");
    }

    @Test
    void processing_event_calls_processing_publish() {
        when(paymentRepo.findById(7L)).thenReturn(Optional.of(payment));

        handler.handlePaymentOutMessage(new PaymentCommittedEvent(7L, "pi_abc", PaymentStatus.PROCESSING));

        verify(publisher).publishPaymentProcessingEvent(payment);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void succeeded_event_calls_succeeded_publish() {
        when(paymentRepo.findById(7L)).thenReturn(Optional.of(payment));

        handler.handlePaymentOutMessage(new PaymentCommittedEvent(7L, "pi_abc", PaymentStatus.SUCCEEDED));

        verify(publisher).publishPaymentSucceededEvent(payment);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void failed_event_calls_failed_publish() {
        when(paymentRepo.findById(7L)).thenReturn(Optional.of(payment));

        handler.handlePaymentOutMessage(new PaymentCommittedEvent(7L, "pi_abc", PaymentStatus.FAILED));

        verify(publisher).publishPaymentFailedEvent(payment);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void canceled_event_calls_canceled_publish() {
        when(paymentRepo.findById(7L)).thenReturn(Optional.of(payment));

        handler.handlePaymentOutMessage(new PaymentCommittedEvent(7L, "pi_abc", PaymentStatus.CANCELED));

        verify(publisher).publishPaymentCanceledEvent(payment);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void missing_payment_no_publish() {
        when(paymentRepo.findById(999L)).thenReturn(Optional.empty());

        handler.handlePaymentOutMessage(new PaymentCommittedEvent(999L, "pi_missing", PaymentStatus.SUCCEEDED));

        verify(publisher, never()).publishPaymentSucceededEvent(any());
        verify(publisher, never()).publishPaymentProcessingEvent(any());
        verify(publisher, never()).publishPaymentFailedEvent(any());
        verify(publisher, never()).publishPaymentCanceledEvent(any());
        verifyNoMoreInteractions(publisher);
    }
}
