package com.retailpulse.payment.infrastructure.config;

import feign.Logger;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

 class FeignConfigTest {
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private static FeignConfig newConfigWithTracer(Tracer tracer) {
        return new FeignConfig(tracerProvider(tracer));
    }

    @Test
    void feignLoggerLevel_full() {
        Tracer tracer = mock(Tracer.class);
        FeignConfig cfg = newConfigWithTracer(tracer);
        Logger.Level level = cfg.feignLoggerLevel();
        assertThat(level).isEqualTo(Logger.Level.FULL);
    }

    @Test
    void interceptor_addsB3Headers_andAuthFromSecurityContext_JwtAuthenticationToken() {
        // tracer with current span
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("trace-123");
        when(ctx.spanId()).thenReturn("span-456");

        FeignConfig cfg = newConfigWithTracer(tracer);
        RequestInterceptor ri = cfg.oauth2BearerForwardingInterceptor();

        Jwt jwt = Jwt.withTokenValue("jwt-token-abc")
                .header("alg", "none")
                .claim("sub", "user1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        RequestTemplate tpl = new RequestTemplate();
        tpl.method(Request.HttpMethod.GET);
        tpl.uri("/api/demo");

        ri.apply(tpl);

        Map<String, Collection<String>> headers = tpl.headers();
        assertThat(headers.get("X-B3-TraceId")).containsExactly("trace-123");
        assertThat(headers.get("X-B3-SpanId")).containsExactly("span-456");
        assertThat(headers.get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer jwt-token-abc");
    }

    @Test
    void interceptor_fallsBackToRequestHeader_whenNoSecurityContext() {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        FeignConfig cfg = newConfigWithTracer(tracer);
        RequestInterceptor ri = cfg.oauth2BearerForwardingInterceptor();

        SecurityContextHolder.clearContext();

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(HttpHeaders.AUTHORIZATION, "Bearer fallback-xyz");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        RequestTemplate tpl = new RequestTemplate();
        tpl.method(Request.HttpMethod.POST);
        tpl.uri("/api/anything");
        ri.apply(tpl);

        assertThat(tpl.headers().get("X-B3-TraceId")).isNull();
        assertThat(tpl.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer fallback-xyz");
    }

    @Test
    void interceptor_noToken_anywhere_means_noAuthorizationHeader() {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        FeignConfig cfg = newConfigWithTracer(tracer);
        RequestInterceptor ri = cfg.oauth2BearerForwardingInterceptor();

        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();

        RequestTemplate tpl = new RequestTemplate();
        tpl.method(Request.HttpMethod.GET);
        tpl.uri("/status");

        ri.apply(tpl);

        assertThat(tpl.headers().get(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<Tracer> tracerProvider(Tracer tracer) {
        ObjectProvider<Tracer> tracerProvider = mock(ObjectProvider.class);
        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        return tracerProvider;
    }
}
