package com.retailpulse.payment.repos;

import com.retailpulse.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Repository;

@EnableJpaRepositories
@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {

    Payment findByTransactionId(String transactionId);


}
