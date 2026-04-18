package com.retailpulse.payment.repos;

import com.retailpulse.payment.model.Payment;
import com.retailpulse.payment.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
 class PaymentRepoTest {

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private TestEntityManager em;

    @Test
    void findByPaymentIntentId_returnsPayment_whenExists() {
        // Arrange
        Payment p = new Payment();
        p.setPaymentIntentId("pi_123");
        p.setTransactionId(1L);
        p.setTotalPrice(1000L);
        p.setCurrency("USD");
        p.setCustomerEmail("aungtun@gmail.com");
        p.setPaymentStatus(PaymentStatus.PROCESSING);
        p.setCreatedDate(Instant.parse("2025-10-13T00:00:00Z").atZone(ZoneOffset.UTC).toLocalDateTime());
        p.setModifiedDate(Instant.parse("2025-10-13T00:00:00Z").atZone(ZoneOffset.UTC).toLocalDateTime());
        p.setCreatedUser(1L);
        p.setModifiedUser(1L);
        em.persistAndFlush(p);
        em.clear();

        // Act
        Optional<Payment> found = paymentRepo.findByPaymentIntentId("pi_123");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getPaymentIntentId()).isEqualTo("pi_123");
        assertThat(found.get().getId()).isNotNull();
    }

    @Test
    void findByPaymentIntentId_returnsEmpty_whenNotExists() {
        // Act
        Optional<Payment> found = paymentRepo.findByPaymentIntentId("does_not_exist");

        // Assert
        assertThat(found).isEmpty();
    }
}
