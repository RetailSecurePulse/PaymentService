package com.retailpulse.payment.payloads;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PaymentData {
    @JsonProperty("transaction_id")
    private Long transactionId;
    @JsonProperty("description")
    private String description;
    @JsonProperty("amount")
    private Double totalPrice;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("customer_email")
    private String customerEmail;
    @JsonProperty("payment_type")
    private String paymentType;
}
