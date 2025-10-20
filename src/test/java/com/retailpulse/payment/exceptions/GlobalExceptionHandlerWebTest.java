package com.retailpulse.payment.exceptions;

import com.stripe.exception.StripeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

 class GlobalExceptionHandlerWebTest {

    private MockMvc mvc;

    @RestController
    static class BoomController {
        private final StripeException stripeEx;

        BoomController(StripeException stripeEx) {
            this.stripeEx = stripeEx;
        }

        @GetMapping("/boom/stripe")
        public void stripe() throws StripeException {
            throw stripeEx;
        }

        @GetMapping("/boom/generic")
        public void generic() {
            throw new RuntimeException("Something bad happened");
        }
    }

    @BeforeEach
    void setup() {
        // Mock a StripeException with a predictable message
        StripeException mockedStripe = mock(StripeException.class);
        when(mockedStripe.getMessage()).thenReturn("Stripe says nope");

        this.mvc = standaloneSetup(new BoomController(mockedStripe))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void stripeException_isHandled_as400_withJsonBody() throws Exception {
        mvc.perform(get("/boom/stripe"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("STRIPE_ERROR"))
                .andExpect(jsonPath("$.message").value("Stripe says nope"));
    }

    @Test
    void genericException_isHandled_as500_withJsonBody() throws Exception {
        mvc.perform(get("/boom/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect((ResultMatcher) jsonPath("$.code").value("GENERIC_ERROR"))
                .andExpect((ResultMatcher) jsonPath("$.message").value("Something bad happened"));
    }
}
