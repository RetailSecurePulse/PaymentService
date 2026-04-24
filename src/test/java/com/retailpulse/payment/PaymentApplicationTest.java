package com.retailpulse.payment;

import com.retailpulse.payment.events.PaymentEvent;
import com.retailpulse.payment.infrastructure.SalesFeignClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
		classes = PaymentApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = {
				"auth.enabled=false",
				"auth.origin=http://localhost",
				"stripe.apiKey=dummy",
				"stripe.webhookSecret=dummy",
				"spring.kafka.bootstrap-servers=localhost:9092"
		}
)
class PaymentApplicationTest {

	@MockitoBean
	KafkaTemplate<String, PaymentEvent> kafkaTemplate;

	@MockitoBean
	SalesFeignClient salesFeignClient;

	@Test
	void contextLoads() {
		// will fail if the Spring context can’t start
	}
}
