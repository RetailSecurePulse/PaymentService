package com.retailpulse.payment.events;

import com.retailpulse.payment.model.PaymentStatus;

public record PaymentCommittedEvent(Long id, String intentId, PaymentStatus status) {
}
