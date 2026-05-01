package com.retailpulse.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class PaymentConfig {
    @Value("${auth.jwt.key.set.uri}")
    private String keySetUri;

    @Value("${auth.enabled}")
    private boolean authEnabled;

    @Value("${auth.origin}")
    private String originURL;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
      if (authEnabled) {
        http.oauth2ResourceServer(c -> c
          .jwt(
            j -> j.jwkSetUri(keySetUri).jwtAuthenticationConverter(jwtAuthenticationConverter())
          )
        );

        http
          .csrf(csrf -> csrf.ignoringRequestMatchers("/api/payments/webhook"))
          .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(c -> c
            .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
            .anyRequest().authenticated() //.hasRole("SUPER").anyRequest().authenticated()
          );
      } else {
        http
          .csrf(AbstractHttpConfigurer::disable)
          .authorizeHttpRequests(request -> request
            .anyRequest().permitAll()
          );
      }

      http.cors(c -> c.configurationSource(corsConfigurationSource()));

      return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
      CorsConfiguration configuration = new CorsConfiguration();
      configuration.setAllowedOriginPatterns(allowedOriginPatterns());
      configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
      configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
      configuration.setExposedHeaders(List.of("Authorization"));
      configuration.setAllowCredentials(true);

      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/**", configuration);
      return source;
    }

    private List<String> allowedOriginPatterns() {
      if (originURL != null && originURL.contains("localhost")) {
        return List.of(originURL, "http://localhost", "http://localhost:*", "https://localhost", "https://localhost:*");
      }
      return List.of(originURL);
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
      JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
      jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter());
      return jwtAuthenticationConverter;
    }

    private JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter() {
      JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
      jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
      jwtGrantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
      return jwtGrantedAuthoritiesConverter;
    }
}
