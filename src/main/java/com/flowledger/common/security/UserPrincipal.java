package com.flowledger.common.security;

import lombok.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;

@Getter @Builder @AllArgsConstructor
public class UserPrincipal implements UserDetails {
    private final UUID id; private final UUID orgId; private final String email; private final String password;
    private final Collection<? extends GrantedAuthority> authorities; private final boolean enabled;
    public static UserPrincipal of(UUID id, UUID orgId, String email, String password, Collection<String> authorities, boolean enabled) {
        return new UserPrincipal(id, orgId, email, password, authorities.stream().map(SimpleGrantedAuthority::new).toList(), enabled);
    }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
}
