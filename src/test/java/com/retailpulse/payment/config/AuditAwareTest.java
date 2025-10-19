package com.retailpulse.payment.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

 class AuditAwareTest {

    private final AuditAware auditAware = new AuditAware();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsZeroWhenAuthenticationIsNull() {
        SecurityContextHolder.clearContext(); // explicit

        Optional<Long> result = auditAware.getCurrentAuditor();

        assertThat(result).isPresent().contains(0L);
    }

    @Test
    void returnsZeroWhenUnauthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> result = auditAware.getCurrentAuditor();

        assertThat(result).isPresent().contains(0L);
        verify(auth, never()).getPrincipal(); // principal should not be touched
    }

    @Test
    void returnsZeroAndTouchesPrincipalWhenAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("user-123");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<Long> result = auditAware.getCurrentAuditor();

        assertThat(result).isPresent().contains(0L);
        verify(auth, times(1)).getPrincipal();
    }
}
