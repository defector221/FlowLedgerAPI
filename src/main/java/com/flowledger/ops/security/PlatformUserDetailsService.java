package com.flowledger.ops.security;

import com.flowledger.ops.entity.PlatformPermission;
import com.flowledger.ops.entity.PlatformRole;
import com.flowledger.ops.entity.PlatformUser;
import com.flowledger.ops.repository.PlatformUserRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserDetailsService {
    private final PlatformUserRepository users;

    public PlatformUserDetailsService(PlatformUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PlatformPrincipal load(UUID userId) {
        PlatformUser user =
                users.findById(userId).orElseThrow(() -> new UsernameNotFoundException("Platform user not found"));
        if (!user.isActive()) {
            throw new UsernameNotFoundException("Platform user inactive");
        }
        return toPrincipal(user);
    }

    public PlatformPrincipal toPrincipal(PlatformUser user) {
        Set<String> roles = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        for (PlatformRole role : user.getRoles()) {
            roles.add(role.getCode());
            for (PlatformPermission p : role.getPermissions()) {
                permissions.add(p.getCode());
            }
        }
        return new PlatformPrincipal(
                user.getId(), user.getEmail(), user.getPasswordHash(), user.isActive(), roles, permissions);
    }
}
