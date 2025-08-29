package com.retailpulse.payment.service;

import com.retailpulse.payment.payloads.PaymentData;
import com.retailpulse.payment.payloads.PaymentResponse;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String STRIPE_PUBLIC_KEY = "pk_test_51Rwa9JCTUDg2faMiUxYG28Di0rDMjD4C5xEPCkn0nv6bPc1Qy8WvivfAhhykVxGlAqfeF2tvILpEd0K9je6WPhLo00bfZAazGS";
    private static final String STRIPE_SECRET_KEY = "sk_test_51Rwa9JCTUDg2faMia0WxH3YMsOxisCmZ9OsuL6Lcl5OF17dkOFF7smfZeBYXWaOxWnIyTeQHvufuKjFQsyS36eVj00cc8enDMQ";


    public PaymentResponse createPaymentIntent(PaymentData data) throws StripeException {
        Stripe.apiKey = STRIPE_SECRET_KEY;
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount((long) (data.getTotalPrice() * 100)) // Amount in cents
                .setCurrency(data.getCurrency())
                .setDescription(data.getDescription())
                .setReceiptEmail(data.getCustomerEmail())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
                )
                .build();
        PaymentIntent paymentIntent = PaymentIntent.create(params);
        return new PaymentResponse(paymentIntent.getClientSecret());
    }
}
