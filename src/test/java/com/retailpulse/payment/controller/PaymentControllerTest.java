package com.retailpulse.payment.controller;

import com.retailpulse.payment.payloads.PaymentData;
import com.retailpulse.payment.payloads.PaymentResponse;
import com.retailpulse.payment.service.PaymentService;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@WithMockUser
 class PaymentControllerTest {

    @Autowired private MockMvc mvc;
    @MockitoBean
    private PaymentService paymentService;

    // ---------- create-payment-intent ----------

    @Test
    void createPaymentIntent_returns200_andCallsService() throws Exception {
        // Given
        PaymentResponse resp = Mockito.mock(PaymentResponse.class);
        when(paymentService.createPaymentIntent(any(PaymentData.class))).thenReturn(resp);

        mvc.perform(
                        post("/api/payments/create-payment-intent")
                                .with(csrf()) // IMPORTANT: avoid 403
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        verify(paymentService).createPaymentIntent(any(PaymentData.class));
    }

    // ---------- webhook ----------

    @Test
    void handleStripeWebhook_invalidSignature_returns400() throws Exception {
        when(paymentService.handleStripeEvent("{payload}", "sig123"))
                .thenReturn("Invalid signature");

        mvc.perform(
                        post("/api/payments/webhook")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", "sig123")
                                .content("{payload}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid signature"));
    }

    @Test
    void handleStripeWebhook_valid_returns200() throws Exception {
        when(paymentService.handleStripeEvent("{payload}","sig-ok"))
                .thenReturn("OK");

        mvc.perform(
                        post("/api/payments/webhook")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", "sig-ok")
                                .content("{payload}")
                )
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // ---------- get-payment-status ----------

    @Test
    void getPaymentStatus_returnsMapWithStatus() throws Exception {
        when(paymentService.getStatus("pi_123")).thenReturn("SUCCEEDED");

        mvc.perform(get("/api/payments/payment-status/{intentId}", "pi_123"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect( jsonPath("$.status", equalTo("SUCCEEDED")));
    }

    // ---------- cancel-payment ----------

    @Test
    void cancelPayment_success_returns200() throws Exception {
        mvc.perform(
                        post("/api/payments/cancel-payment/{intentId}", "pi_123")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().string("Canceled"));

        verify(paymentService).cancelPayment("pi_123");
    }

    @Test
    void cancelPayment_stripeError_returns400() throws Exception {
        // Make the service throw a StripeException
        StripeException ex = Mockito.mock(StripeException.class);
        doThrow(ex).when(paymentService).cancelPayment("pi_bad");

        mvc.perform(
                        post("/api/payments/cancel-payment/{intentId}", "pi_bad")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cancellation Failed"));

        verify(paymentService).cancelPayment("pi_bad");
    }
}