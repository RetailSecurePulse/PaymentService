package com.retailpulse.payment.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

 class ErrorResponseTest {

    @Test
    void constructor_setsFields() {
        ErrorResponse er = new ErrorResponse("E001", "Something went wrong");

        assertThat(er.getCode()).isEqualTo("E001");
        assertThat(er.getMessage()).isEqualTo("Something went wrong");
    }

    @Test
    void setters_updateFields() {
        ErrorResponse er = new ErrorResponse("X", "Y");
        er.setCode("E002");
        er.setMessage("New message");

        assertThat(er.getCode()).isEqualTo("E002");
        assertThat(er.getMessage()).isEqualTo("New message");
    }
}
