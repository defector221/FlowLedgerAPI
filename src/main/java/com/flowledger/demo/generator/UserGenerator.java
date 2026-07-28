package com.flowledger.demo.generator;

import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.auth.repository.RoleRepository;
import com.flowledger.auth.repository.UserRepository;
import com.flowledger.auth.service.OrganizationMembershipService;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.config.DemoProperties;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates demo org users with role-based membership (skips missing roles). */
@Component
public class UserGenerator {
    private static final Logger log = LoggerFactory.getLogger(UserGenerator.class);

    private final DemoProperties demoProperties;
    private final UserRepository users;
    private final RoleRepository roles;
    private final OrganizationRepository organizations;
    private final OrganizationMembershipService memberships;
    private final OrganizationMembershipRepository membershipRepo;
    private final PasswordEncoder encoder;

    public UserGenerator(
            DemoProperties demoProperties,
            UserRepository users,
            RoleRepository roles,
            OrganizationRepository organizations,
            OrganizationMembershipService memberships,
            OrganizationMembershipRepository membershipRepo,
            PasswordEncoder encoder) {
        this.demoProperties = demoProperties;
        this.users = users;
        this.roles = roles;
        this.organizations = organizations;
        this.memberships = memberships;
        this.membershipRepo = membershipRepo;
        this.encoder = encoder;
    }

    public record DemoUserSpec(String localPart, String firstName, String lastName, String roleCode) {}

    /**
     * Creates or reuses a user for the demo org. Returns empty when the role code is not seeded.
     */
    public Optional<UUID> createOrgUser(
            DemoSeedContext ctx, DemoUserSpec spec, ProgressLogger progress, boolean primaryAdmin) {
        Optional<Role> roleOpt = roles.findByCode(spec.roleCode());
        if (roleOpt.isEmpty()) {
            log.warn(
                    "[{}] Skipping user {} — role {} not found",
                    ctx.getScenario().slug(),
                    spec.localPart(),
                    spec.roleCode());
            return Optional.empty();
        }
        Role role = roleOpt.get();
        UUID orgId = ctx.getOrganizationId();
        Organization org = organizations.findById(orgId).orElseThrow();
        String email = demoEmail(ctx, spec.localPart());

        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setFirstName(spec.firstName());
            user.setLastName(spec.lastName());
            user.setPasswordHash(encoder.encode(demoProperties.getDefaultPassword()));
            user.setActive(true);
            user.setEmailVerified(true);
            user.setUserStatus("ACTIVE");
            user.setOrganizationId(orgId);
            user.setLastActiveOrganizationId(orgId);
            user.getRoles().add(role);
            user = users.save(user);
            progress.stage("Created user " + email);
        } else {
            if (user.getRoles().stream().noneMatch(r -> spec.roleCode().equals(r.getCode()))) {
                user.getRoles().add(role);
            }
            user.setActive(true);
            user.setUserStatus("ACTIVE");
            user.setOrganizationId(orgId);
            user.setLastActiveOrganizationId(orgId);
            users.save(user);
        }

        ensureMembership(user, org, role, primaryAdmin);
        return Optional.of(user.getId());
    }

    public static String demoEmail(DemoSeedContext ctx, String localPart) {
        return localPart.toLowerCase(Locale.ROOT) + "@" + ctx.getScenario().slug() + ".demo";
    }

    private void ensureMembership(User user, Organization org, Role role, boolean primaryAdmin) {
        var existing = membershipRepo.findByUserIdAndOrganizationId(user.getId(), org.getId());
        if (existing.isPresent()) {
            OrganizationMembership m = existing.get();
            m.setStatus("ACTIVE");
            if (m.getRoles().stream().noneMatch(r -> role.getCode().equals(r.getCode()))) {
                m.getRoles().add(role);
            }
            membershipRepo.save(m);
            return;
        }
        if (primaryAdmin) {
            memberships.createAdminMembership(user, org, role);
        } else {
            memberships.inviteToOrganization(org.getId(), user, role, true);
        }
    }
}
