package com.example.tsumory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.tsumory.domain.User;
import com.example.tsumory.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private static final String HASH = "$2a$10$hash";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void ユーザー名で見つかればIDとハッシュを持つCustomUserDetailsを返す() {
        var user = new User("demo", HASH, Instant.parse("2026-10-01T00:00:00Z"));
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(user));

        var userDetails = service.loadUserByUsername("demo");

        assertThat(userDetails).isEqualTo(new CustomUserDetails(1L, "demo", HASH));
        assertThat(userDetails.getUsername()).isEqualTo("demo");
        assertThat(userDetails.getPassword()).isEqualTo(HASH);
    }

    @Test
    void 見つからなければUsernameNotFoundException() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody")).isInstanceOf(UsernameNotFoundException.class);
    }
}
