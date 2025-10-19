package com.retailpulse.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.AuditorAware;

import static org.assertj.core.api.Assertions.assertThat;

 class AuditConfigTest {

    @Test
    void auditRefFactoryMethodReturnsInstance() {
        AuditConfig cfg = new AuditConfig();

        AuditAware instance = cfg.auditRef();

        assertThat(instance).isNotNull();
        assertThat(instance).isInstanceOf(AuditAware.class);
        // optional: each call returns a new instance
        assertThat(cfg.auditRef()).isNotSameAs(instance);
    }

    @Test
    void componentBeanNamed_auditRef_isPresentInContext() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.register(AuditAware.class);
            ctx.refresh();

            Object bean = ctx.getBean("auditRef");
            assertThat(bean).isInstanceOf(AuditorAware.class);
            assertThat(bean).isInstanceOf(AuditAware.class);
        }
    }
}
