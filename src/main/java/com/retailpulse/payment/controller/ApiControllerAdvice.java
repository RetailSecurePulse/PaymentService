package com.retailpulse.payment.controller;


import com.stripe.exception.StripeException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiControllerAdvice {

    private static final Logger logger = LoggerFactory.getLogger(ApiControllerAdvice.class);

    @ExceptionHandler(StripeException.class)
    public ResponseEntity<Map<String, String>> stripeExceptionHandler(StripeException exception) {
        logger.error("Stripe Error", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "STRIPE_ERROR", "message", exception.getMessage()));
    }

    public ResponseEntity<Map<String, String>> genericExceptionHandler(Exception exception) {
        logger.error("Internal Server Error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "INTERNAL_SERVER_ERROR", "message", exception.getMessage()));
    }

}
