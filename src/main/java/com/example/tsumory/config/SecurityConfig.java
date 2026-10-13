package com.example.tsumory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 認証なしで開けるのは、ログイン画面と、実際に置いている静的リソース（独自の CSS・JS と Bootstrap）だけ。
        // POST /login・POST /logout はこの認可より前のフィルターで処理され、
        // /login?error・/login?logout は "/login" に一致するので、formLogin・logout に permitAll() は付けない。
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/login", "/css/**", "/js/**", "/webjars/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
