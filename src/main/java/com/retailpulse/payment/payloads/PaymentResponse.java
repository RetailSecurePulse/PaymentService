package com.retailpulse.payment.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.retailpulse.payment.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    @JsonProperty("clientSecret")
    private String clientSecret;
    @JsonProperty("paymentIntentId")
    private String paymentIntentId;
    @JsonProperty("paymentId")
    private Long paymentId;
    @JsonProperty("transactionId")
    private String transactionId;
    @JsonProperty("totalPrice")
    private Double totalPrice;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("paymentStatus")
    private PaymentStatus paymentStatus;
    @JsonProperty("paymentDate")
    private LocalDateTime paymentDate;
}
