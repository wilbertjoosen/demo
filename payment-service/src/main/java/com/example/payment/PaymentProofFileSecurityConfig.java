package com.example.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * <img>/<a href> tags can't attach an Authorization header, so served proof files need to be
 * publicly readable — same rationale and @Order(1) pre-empting pattern as product-media-service's
 * MediaFileSecurityConfig. Uploading (POST /api/payments/{id}/proof) stays behind the normal
 * authenticated-and-ownership-checked path.
 */
@Configuration
public class PaymentProofFileSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain paymentProofFileSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/payments/files/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
