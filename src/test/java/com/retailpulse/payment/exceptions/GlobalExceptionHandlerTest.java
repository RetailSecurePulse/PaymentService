package com.retailpulse.payment.exceptions;

import com.retailpulse.payment.controller.ErrorResponse;
import com.stripe.exception.StripeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Objects;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleStripeException_returns400_andBody() {
        StripeException ex = mock(StripeException.class);
        when(ex.getMessage()).thenReturn("Card was declined");

        ResponseEntity<ErrorResponse> resp = handler.handleStripeException(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(Objects.requireNonNull(resp.getBody()).getCode()).isEqualTo("STRIPE_ERROR");
        assertThat(resp.getBody().getMessage()).isEqualTo("Card was declined");
    }

    @Test
    void handleGenericException_returns500_andBody() {
        Exception ex = new RuntimeException("Boom!");

        ResponseEntity<ErrorResponse> resp = handler.handleGenericException(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo("GENERIC_ERROR");
        assertThat(resp.getBody().getMessage()).isEqualTo("Boom!");
    }
}
