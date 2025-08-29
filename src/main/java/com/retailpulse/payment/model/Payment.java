package com.retailpulse.payment.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@EntityListeners(AuditingEntityListener.class)
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_seq")
    @SequenceGenerator(name = "payment_seq", allocationSize = 1)
    @Column(name = "payment_id", nullable = false)
    private Long id;
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;
    @Column(name = "description")
    private String description;
    @Column(name = "total_price", nullable = false)
    private Long totalPrice;
    @Column(name = "currency", nullable = false)
    private String currency;
    @Column(name = "customer_email")
    private String customerEmail;

    @CreatedDate
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "modified_date", nullable = false)
    private LocalDateTime modifiedDate;

    @CreatedBy
    @Column(name = "created_user", nullable = false)
    private Long createdUser;
    @LastModifiedBy
    @Column(name = "modified_user", nullable = false)
    private Long modifiedUser;

}
