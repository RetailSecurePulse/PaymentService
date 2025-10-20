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
        AuditAware another = cfg.auditRef();

        assertThat(instance)
                .isNotNull()
                .isInstanceOf(AuditAware.class)
                .isNotSameAs(another);
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
