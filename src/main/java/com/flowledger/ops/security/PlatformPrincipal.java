package com.flowledger.ops.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class PlatformPrincipal implements UserDetails {
    private final UUID id;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public PlatformPrincipal(
            UUID id, String email, String password, boolean enabled, Set<String> roles, Set<String> permissions) {
        this(id, email, password, enabled, roles, permissions, null);
    }

    public PlatformPrincipal(
            UUID id,
            String email,
            String password,
            boolean enabled,
            Set<String> roles,
            Set<String> permissions,
            Collection<String> extraAuthorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.enabled = enabled;
        this.roles = roles;
        this.permissions = permissions;
        Set<GrantedAuthority> granted = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        for (String role : roles) {
            granted.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
        }
        if (extraAuthorities != null) {
            for (String authority : extraAuthorities) {
                granted.add(new SimpleGrantedAuthority(authority));
            }
        }
        this.authorities = granted;
    }

    public boolean hasPermission(String code) {
        return permissions.contains(code) || roles.contains("PLATFORM_ADMIN");
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
