package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class CustomUserDetailsTest {

    private final CustomUserDetails userDetails = new CustomUserDetails(1L, "demo", "$2a$10$hash");

    @Test
    void toStringにパスワードのハッシュを出さない() {
        assertThat(userDetails.toString())
                .isEqualTo("CustomUserDetails[userId=1, username=demo, passwordHash=[PROTECTED]]")
                .doesNotContain("$2a$10$hash");
    }

    @Test
    void 権限はROLE_USER() {
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }
}
