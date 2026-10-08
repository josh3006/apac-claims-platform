package com.example.claims;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class SecurityConfig {
    @Bean
    UserDetailsService users(@Value("${app.claimant-password}") String claimantPassword,
                             @Value("${app.officer-password}") String officerPassword) {
        var encoder = new BCryptPasswordEncoder();
        return new InMemoryUserDetailsManager(
            User.withUsername("claimant").password(encoder.encode(claimantPassword)).roles("CLAIMANT").build(),
            User.withUsername("officer").password(encoder.encode(officerPassword)).roles("OFFICER").build());
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        // API uses explicitly supplied Authorization headers; no cookie/session authentication.
        return http.csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/officer/**").hasRole("OFFICER")
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults()).build();
    }
}
