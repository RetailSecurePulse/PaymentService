package com.retailpulse.payment.controller;

import com.stripe.exception.StripeException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


 class ApiControllerAdviceTest {

    private final ApiControllerAdvice advice = new ApiControllerAdvice();

    @Test
    void stripeExceptionHandler_returns502_andBody() {
        // given
        StripeException ex = Mockito.mock(StripeException.class);
        Mockito.when(ex.getMessage()).thenReturn("boom");

        // when
        ResponseEntity<Map<String, String>> resp = advice.stripeExceptionHandler(ex);

        // then
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resp.getBody())
                .isNotNull()
                .containsEntry("error", "STRIPE_ERROR")
                .containsEntry("message", "boom");
    }

    @Test
    void genericExceptionHandler_returns500_andBody() {
        // given
        Exception ex = new Exception("oops");

        // when
        ResponseEntity<Map<String, String>> resp = advice.genericExceptionHandler(ex);

        // then
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody())
                .isNotNull()
                .containsEntry("error", "INTERNAL_SERVER_ERROR")
                .containsEntry("message", "oops");
    }
}
