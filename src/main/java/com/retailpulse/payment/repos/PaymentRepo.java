package com.retailpulse.payment.repos;

import com.retailpulse.payment.model.Payment;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@EnableJpaRepositories
@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {

    @Transactional
    @Query("SELECT t FROM Payment t WHERE t.transactionId = :transactionId")
    Payment findByTransactionId(@Param("transactionId") String transactionId);

    @Transactional
    @Query("SELECT t FROM Payment t WHERE t.paymentIntentId = :paymentIntentId")
    Optional<Payment> findByPaymentIntentId(@Param("paymentIntentId") String paymentIntentId);

}
