package com.retailpulse.payment.config;


import com.retailpulse.payment.controller.PaymentController;
import com.retailpulse.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PaymentController.class)
@Import(PaymentConfig.class)
@TestPropertySource(properties = {
        "auth.enabled=true",
        "auth.origin=http://localhost",
        // any non-null value so the bean builds; we use .with(jwt()) in tests
        "auth.jwt.key.set.uri=http://localhost/fake-jwks"
})
 class PaymentConfigSecurityEnabledTest {

    @Autowired private MockMvc mvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void webhook_is_open_without_token_when_auth_enabled() throws Exception {
        when(paymentService.handleStripeEvent("{payload}", "sig")).thenReturn("OK");

        mvc.perform(post("/api/payments/webhook")
                        .contentType(APPLICATION_JSON)
                        .header("Stripe-Signature", "sig")
                        .content("{payload}"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void other_api_paths_require_auth_when_auth_enabled() throws Exception {
        mvc.perform(get("/api/payments/payment-status/{intentId}", "pi_123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void other_api_paths_with_mock_jwt_are_ok_when_auth_enabled() throws Exception {
        when(paymentService.getStatus("pi_123")).thenReturn("SUCCEEDED");

        mvc.perform(get("/api/payments/payment-status/{intentId}", "pi_123").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }
}
