package com.example.tsumory.config;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** ログイン中のユーザー。controller は {@code @AuthenticationPrincipal} で受け、{@link #userId()} を service に渡す。 */
public record CustomUserDetails(Long userId, String username, String passwordHash) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    // record の既定の toString はパスワードのハッシュも出すため、ログに出ないよう伏せる
    @Override
    public String toString() {
        return "CustomUserDetails[userId=" + userId + ", username=" + username + ", passwordHash=[PROTECTED]]";
    }
}
