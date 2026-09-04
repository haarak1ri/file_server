package com.example.file_server.config;

import com.example.file_server.service.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(UserDetailsServiceImpl userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register").permitAll()  // регистрация без пароля
                        .anyRequest().authenticated()
                )
                .httpBasic(httpBasic -> {})
                .userDetailsService(userDetailsService);  // ← использовать вашу БД

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

//Cross-Site Request Forgery — атака, когда злой сайт отправляет запрос от имени пользователя
//Для REST API (который не использует cookies/сессии) CSRF не нужен.
//authorizeHttpRequests настраиваем какие url кому доступны
//Кто угодно может зарегистрироваться по адресу /api/auth/register.
//А для всех остальных запросов нужно ввести логин и пароль.