package com.flowledger.common.security;

import java.util.*;
import lombok.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    private final UUID id;
    private final UUID orgId;
    private final UUID branchId;
    private final UUID storeId;
    private final UUID warehouseId;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;

    public static UserPrincipal of(
            UUID id, UUID orgId, String email, String password, Collection<String> authorities, boolean enabled) {
        return of(id, orgId, null, null, null, email, password, authorities, enabled);
    }

    public static UserPrincipal of(
            UUID id,
            UUID orgId,
            UUID branchId,
            UUID storeId,
            UUID warehouseId,
            String email,
            String password,
            Collection<String> authorities,
            boolean enabled) {
        return new UserPrincipal(
                id,
                orgId,
                branchId,
                storeId,
                warehouseId,
                email,
                password,
                authorities.stream().map(SimpleGrantedAuthority::new).toList(),
                enabled);
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
