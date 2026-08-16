package com.example.payment.config;

import com.example.payment.service.PaymentGatewayClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * /internal/gateway-simulator/** is called only by this same service's own loopback WebClient
 * (PaymentGatewayClient) — permitted here rather than requiring a JWT, same pattern as
 * notification-service's WebSocketSecurityConfig for /ws/**.
 */
@Configuration
public class InternalEndpointSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
