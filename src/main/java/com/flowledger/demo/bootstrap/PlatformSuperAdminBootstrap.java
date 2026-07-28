package com.flowledger.demo.bootstrap;

import com.flowledger.accounting.service.ChartOfAccountsBootstrapService;
import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.auth.repository.RoleRepository;
import com.flowledger.auth.repository.UserRepository;
import com.flowledger.auth.service.OrganizationMembershipService;
import com.flowledger.demo.config.PlatformSuperAdminProperties;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.platform.service.EditionService;
import com.flowledger.subscription.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotently ensures a platform SUPER_ADMIN user exists when password is configured in yaml/env.
 */
@Component
@Order(1)
public class PlatformSuperAdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlatformSuperAdminBootstrap.class);
    public static final String PLATFORM_ORG_NAME = "FlowLedger Platform";

    private final PlatformSuperAdminProperties props;
    private final UserRepository users;
    private final RoleRepository roles;
    private final OrganizationRepository organizations;
    private final OrganizationSettingsRepository settings;
    private final OrganizationMembershipService memberships;
    private final OrganizationMembershipRepository membershipRepo;
    private final PasswordEncoder encoder;
    private final SubscriptionService subscriptions;
    private final EditionService editions;
    private final ChartOfAccountsBootstrapService accounting;

    public PlatformSuperAdminBootstrap(
            PlatformSuperAdminProperties props,
            UserRepository users,
            RoleRepository roles,
            OrganizationRepository organizations,
            OrganizationSettingsRepository settings,
            OrganizationMembershipService memberships,
            OrganizationMembershipRepository membershipRepo,
            PasswordEncoder encoder,
            SubscriptionService subscriptions,
            EditionService editions,
            ChartOfAccountsBootstrapService accounting) {
        this.props = props;
        this.users = users;
        this.roles = roles;
        this.organizations = organizations;
        this.settings = settings;
        this.memberships = memberships;
        this.membershipRepo = membershipRepo;
        this.encoder = encoder;
        this.subscriptions = subscriptions;
        this.editions = editions;
        this.accounting = accounting;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.hasPassword()) {
            log.info("Platform SUPER_ADMIN bootstrap skipped (flowledger.platform.super-admin.password blank)");
            return;
        }
        Role superRole = roles.findByCode("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing — run Flyway V7"));

        Organization org = organizations.findByNameIgnoreCase(PLATFORM_ORG_NAME).orElseGet(this::createPlatformOrg);

        String email = props.getEmail().trim().toLowerCase();
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setFirstName(props.getFirstName());
            user.setLastName(props.getLastName());
            user.setPasswordHash(encoder.encode(props.getPassword()));
            user.setActive(true);
            user.setEmailVerified(true);
            user.setUserStatus("ACTIVE");
            user.setOrganizationId(org.getId());
            user.setLastActiveOrganizationId(org.getId());
            user.getRoles().add(superRole);
            user = users.save(user);
            ensureSuperMembership(user, org, superRole);
            subscriptions.ensureDefaultSubscription(user.getId(), "FREE");
            subscriptions.ensureOrganizationSubscription(org.getId(), "FREE");
            editions.provisionNewOrganization(org.getId(), "FREE", user.getId());
            log.info("Created platform SUPER_ADMIN user {}", email);
        } else {
            if (user.getRoles().stream().noneMatch(r -> "SUPER_ADMIN".equals(r.getCode()))) {
                user.getRoles().add(superRole);
            }
            if (props.isResetPasswordOnStartup()) {
                user.setPasswordHash(encoder.encode(props.getPassword()));
                log.info("Reset password for platform SUPER_ADMIN {}", email);
            }
            user.setActive(true);
            user.setUserStatus("ACTIVE");
            user.setOrganizationId(org.getId());
            user.setLastActiveOrganizationId(org.getId());
            users.save(user);
            ensureSuperMembership(user, org, superRole);
            log.info("Ensured platform SUPER_ADMIN user {}", email);
        }
    }

    private void ensureSuperMembership(User user, Organization org, Role superRole) {
        var existing = membershipRepo.findByUserIdAndOrganizationId(user.getId(), org.getId());
        if (existing.isEmpty()) {
            memberships.createAdminMembership(user, org, superRole);
            return;
        }
        OrganizationMembership m = existing.get();
        m.setStatus("ACTIVE");
        if (m.getRoles().stream().noneMatch(r -> "SUPER_ADMIN".equals(r.getCode()))) {
            m.getRoles().add(superRole);
        }
        membershipRepo.save(m);
    }

    private Organization createPlatformOrg() {
        Organization org = new Organization();
        org.setName(PLATFORM_ORG_NAME);
        org.setEmail("platform@flowledger.local");
        org.setCountry("India");
        org.setCurrency("INR");
        org.setFinancialYearStart("04-01");
        org.setInvoicePrefix("PLT");
        org.setInvoiceNumberFormat("{PREFIX}/{FY}/{SEQ:6}");
        org.setOnboardingCompleted(true);
        org = organizations.save(org);
        OrganizationSettings s = new OrganizationSettings();
        s.setOrganizationId(org.getId());
        settings.save(s);
        accounting.bootstrapOrganization(org.getId(), org.getFinancialYearStart());
        return org;
    }
}
