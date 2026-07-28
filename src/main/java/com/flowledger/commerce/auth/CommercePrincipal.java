package com.flowledger.commerce.auth;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Getter
public class CommercePrincipal {
    public static final String ROLE = "COMMERCE_CUSTOMER";

    private final UUID customerId;
    private final String mobile;

    public CommercePrincipal(UUID customerId, String mobile) {
        this.customerId = customerId;
        this.mobile = mobile;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ROLE));
    }
}
