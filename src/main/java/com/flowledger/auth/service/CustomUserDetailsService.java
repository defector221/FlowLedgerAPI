package com.flowledger.auth.service;

import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.auth.repository.UserRepository;
import com.flowledger.common.exception.UnauthorizedException;
import com.flowledger.common.security.UserPrincipal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements org.springframework.security.core.userdetails.UserDetailsService {
    private final UserRepository users;
    private final OrganizationMembershipRepository memberships;

    public CustomUserDetailsService(UserRepository users, OrganizationMembershipRepository memberships) {
        this.users = users;
        this.memberships = memberships;
    }

    @Override
    public UserDetails loadUserByUsername(String id) {
        return load(UUID.fromString(id), null);
    }

    public UserPrincipal load(UUID userId, UUID organizationId) {
        User user =
                users.findByIdAndActiveTrue(userId).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        UUID activeOrgId = organizationId != null ? organizationId : user.getLastActiveOrganizationId();
        if (activeOrgId == null) {
            throw new UnauthorizedException("Active organization is required");
        }
        OrganizationMembership membership = memberships
                .findByUserIdAndOrganizationId(user.getId(), activeOrgId)
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .orElseThrow(() -> new UnauthorizedException("Organization access denied"));
        Set<String> authorities = new HashSet<>();
        membership.getRoles().forEach(role -> {
            authorities.add("ROLE_" + role.getCode());
            role.getPermissions().forEach(permission -> authorities.add(permission.getCode()));
        });
        return UserPrincipal.of(
                user.getId(), activeOrgId, user.getEmail(), user.getPasswordHash(), authorities, user.isActive());
    }
}
