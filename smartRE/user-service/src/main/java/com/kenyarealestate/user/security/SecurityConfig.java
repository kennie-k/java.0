package com.kenyarealestate.user.security;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtFilter;
    private final InternalSecretFilter internalSecretFilter;
    public SecurityConfig(JwtAuthenticationFilter f, InternalSecretFilter internalSecretFilter) {
        this.jwtFilter = f;
        this.internalSecretFilter = internalSecretFilter;
    }

    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/auth/register","/api/auth/login","/api/auth/logout",
                        "/api/auth/forgot-password","/api/auth/reset-password").permitAll()
                .requestMatchers("/api/auth/**","/swagger-ui/**","/v3/api-docs/**","/actuator/**").permitAll()
                .requestMatchers(HttpMethod.GET,
                        "/api/documents/files/documents/property_image/**",
                        "/api/documents/files/documents/profile_image/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/documents/internal/files/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/documents/files/**").authenticated()
                .requestMatchers("/api/users/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/agent-applications/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/users/me", "/api/users/me/password").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/users/{id}").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(internalSecretFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
