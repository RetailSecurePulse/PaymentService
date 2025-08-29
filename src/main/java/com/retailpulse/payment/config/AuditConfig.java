package com.retailpulse.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditRef")
public class AuditConfig  {

	public AuditAware auditRef() {
		return new AuditAware();
	}

}
