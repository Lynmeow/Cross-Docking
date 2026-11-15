package com.crossdock.config;

import com.crossdock.security.LoginFailureHandler;
import com.crossdock.security.LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginSuccessHandler successHandler,
                                                   LoginFailureHandler failureHandler) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error", "/access-denied", "/favicon.ico").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/audit/**").hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers("/dashboard/**", "/docks/**").hasRole("MANAGER")
                .requestMatchers("/inbound/**", "/labels/**").hasAnyRole("INBOUND", "MANAGER")
                .requestMatchers("/outbound/**").hasAnyRole("OUTBOUND", "MANAGER")
                .requestMatchers("/trips/**", "/pallets/**", "/incidents/**").hasAnyRole("MANAGER", "INBOUND", "OUTBOUND")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll())
            .exceptionHandling(ex -> ex.accessDeniedPage("/access-denied"));
        return http.build();
    }
}