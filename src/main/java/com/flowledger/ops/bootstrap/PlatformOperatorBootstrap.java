package com.flowledger.ops.bootstrap;

import com.flowledger.demo.config.PlatformSuperAdminProperties;
import com.flowledger.ops.entity.PlatformRole;
import com.flowledger.ops.entity.PlatformUser;
import com.flowledger.ops.repository.PlatformRoleRepository;
import com.flowledger.ops.repository.PlatformUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bootstraps platform.users PLATFORM_ADMIN from yaml (separate from tenant SUPER_ADMIN). */
@Component
@Order(2)
public class PlatformOperatorBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlatformOperatorBootstrap.class);

    private final PlatformSuperAdminProperties props;
    private final PlatformUserRepository users;
    private final PlatformRoleRepository roles;
    private final PasswordEncoder encoder;

    public PlatformOperatorBootstrap(
            PlatformSuperAdminProperties props,
            PlatformUserRepository users,
            PlatformRoleRepository roles,
            PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.hasPassword()) {
            log.info("Platform operator bootstrap skipped (password blank)");
            return;
        }
        PlatformRole adminRole = roles.findByCode("PLATFORM_ADMIN")
                .orElseThrow(() -> new IllegalStateException("PLATFORM_ADMIN role missing — run Flyway V74"));
        String email = props.getEmail().trim().toLowerCase();
        PlatformUser user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            user = new PlatformUser();
            user.setEmail(email);
            user.setFirstName(props.getFirstName());
            user.setLastName(props.getLastName());
            user.setPasswordHash(encoder.encode(props.getPassword()));
            user.setActive(true);
            user.getRoles().add(adminRole);
            users.save(user);
            log.info("Created platform PLATFORM_ADMIN operator {}", email);
            return;
        }
        if (user.getRoles().stream().noneMatch(r -> "PLATFORM_ADMIN".equals(r.getCode()))) {
            user.getRoles().add(adminRole);
        }
        if (props.isResetPasswordOnStartup()) {
            user.setPasswordHash(encoder.encode(props.getPassword()));
            log.info("Reset password for platform operator {}", email);
        }
        user.setActive(true);
        users.save(user);
        log.info("Ensured platform operator {}", email);
    }
}
