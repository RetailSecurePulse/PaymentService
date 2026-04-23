package com.retailpulse.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

 class PaymentApplicationMainTest {

     @Test
     void main_doesNotThrow() {
         try (MockedStatic<SpringApplication> springApplication = Mockito.mockStatic(SpringApplication.class)) {
             springApplication.when(() -> SpringApplication.run(PaymentApplication.class, new String[]{}))
                     .thenReturn(Mockito.mock(ConfigurableApplicationContext.class));

             Executable run = () -> PaymentApplication.main(new String[]{});
             assertDoesNotThrow(run);
         }
     }
}
