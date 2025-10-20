package com.retailpulse.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

 class PaymentApplicationMainTest {

     @Test
     void main_doesNotThrow() {
         Executable run = () -> PaymentApplication.main(new String[]{});
         assertDoesNotThrow(run);
     }
}
