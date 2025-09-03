package com.retailpulse.payment.model;

public class Constants {
    public static final String PAYMENT_PROCESSING = "Payment is being processed.";
    public static final String PAYMENT_SUCCESS = "Payment was successful.";
    public static final String PAYMENT_FAILED = "Payment failed.";
    public static final String PAYMENT_CANCELED = "Payment was canceled.";
    public static final String INVALID_SIGNATURE = "Invalid signature";
    public static final String PAYMENT_NOT_FOUND = "Payment not found.";
    public static final String STRIPE_PAYMENT_RECEIVED = "received";
    public static final String PAYMENT_SUCCESS_WEBHOOK = "payment_intent.succeeded";
    public static final String PAYMENT_FAILED_WEBHOOK = "payment_intent.payment_failed";
    public static final String PAYMENT_CANCELED_WEBHOOK = "payment_intent.canceled";
    public static final String PAYMENT_INTENT_CREATED_WEBHOOK = "payment_intent.created";
}
