package com.retailpulse.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import({PaymentConfigTest.DisabledSecurity.DummyController.class, PaymentConfig.class})
@TestPropertySource(properties = {
        "auth.enabled=false",
        "auth.origin=http://frontend.test",
        "auth.jwt.key.set.uri=http://localhost/jwks"
})
 class PaymentConfigTest {

    @RestController
    static class DisabledSecurity {
        @RestController
        static class DummyController {
            @GetMapping("/api/secure")
            public String secure() { return "ok"; }
            @GetMapping("/public")
            public String pub() { return "pub"; }
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void whenAuthDisabled_everythingIsPermitted() throws Exception {
        mvc.perform(get("/api/secure"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        mvc.perform(get("/public"))
                .andExpect(status().isOk())
                .andExpect(content().string("pub"));
    }

    @Test
    void corsPreflight_allowedWithConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/secure")
                        .header("Origin", "http://frontend.test")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://frontend.test"));
    }
}
