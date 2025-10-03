package com.retailpulse.payment.events;

import com.retailpulse.payment.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
public class PaymentEvent {

    private Long paymentId;
    private String paymentIntentId;
    private String transactionId;
    private Long totalPrice;
    private String currency;
    private String customerEmail;
    private PaymentStatus paymentStatus;
    private LocalDateTime paymentDate;
}
