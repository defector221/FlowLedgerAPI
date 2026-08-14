package com.flowledger.iam.service;

import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.auth.repository.UserRepository;
import com.flowledger.iam.client.IamDtos;
import com.flowledger.ops.entity.PlatformUser;
import com.flowledger.ops.repository.PlatformUserRepository;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Upserts local shells keyed by IAM ids only. Does not duplicate IAM authorization.
 */
@Service
@Slf4j
public class IamShellProvisioningService {
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationSettingsRepository settings;
    private final OrganizationMembershipRepository memberships;
    private final PlatformUserRepository platformUsers;
    private final PasswordEncoder passwordEncoder;

    public IamShellProvisioningService(
            UserRepository users,
            OrganizationRepository organizations,
            OrganizationSettingsRepository settings,
            OrganizationMembershipRepository memberships,
            PlatformUserRepository platformUsers,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.organizations = organizations;
        this.settings = settings;
        this.memberships = memberships;
        this.platformUsers = platformUsers;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ShellResult provisionTenant(IamDtos.MeResult me) {
        if (me == null || me.userId() == null || me.organizationId() == null) {
            throw new IllegalArgumentException("IAM identity snapshot missing userId/organizationId");
        }
        Organization org = organizations
                .findByIamOrganizationId(me.organizationId())
                .orElseGet(Organization::new);
        if (org.getId() == null) {
            org.setName(StringUtils.hasText(me.organizationName()) ? me.organizationName() : "IAM Organization");
            org.setOnboardingCompleted(false);
            org.setEditionCode("PROFESSIONAL");
            org.setActive(true);
            org.setLifecycleStatus("ACTIVE");
        } else if (StringUtils.hasText(me.organizationName())) {
            org.setName(me.organizationName());
        }
        org.setIamOrganizationId(me.organizationId());
        // Defaults expected by invoice/settings flows; register() path creates these too.
        if (!StringUtils.hasText(org.getCountry())) org.setCountry("India");
        if (!StringUtils.hasText(org.getCurrency())) org.setCurrency("INR");
        if (!StringUtils.hasText(org.getFinancialYearStart())) org.setFinancialYearStart("04-01");
        if (!StringUtils.hasText(org.getInvoicePrefix())) org.setInvoicePrefix("INV");
        if (!StringUtils.hasText(org.getInvoiceNumberFormat())) org.setInvoiceNumberFormat("{PREFIX}/{FY}/{SEQ:6}");
        org = organizations.save(org);
        ensureSettings(org.getId());

        User user = users.findByIamUserId(me.userId()).orElseGet(() -> {
            // Prefer linking by email if an existing local account matches.
            if (StringUtils.hasText(me.email())) {
                return users.findByEmailIgnoreCase(me.email()).orElseGet(User::new);
            }
            return new User();
        });
        if (user.getId() == null) {
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID() + ":iam-sso"));
            user.setActive(true);
            user.setEmailVerified(true);
            user.setUserStatus("ACTIVE");
        }
        user.setIamUserId(me.userId());
        user.setEmail(StringUtils.hasText(me.email()) ? me.email() : "iam-" + me.userId() + "@sankhya.local");
        String[] names = splitName(me.displayName());
        user.setFirstName(names[0]);
        user.setLastName(names[1]);
        user.setOrganizationId(org.getId());
        user.setLastActiveOrganizationId(org.getId());
        user = users.save(user);

        final UUID userId = user.getId();
        final UUID orgId = org.getId();
        OrganizationMembership membership = memberships
                .findByUserIdAndOrganizationId(userId, orgId)
                .orElseGet(() -> {
                    OrganizationMembership created = new OrganizationMembership();
                    created.setUserId(userId);
                    created.setOrganizationId(orgId);
                    created.setStatus("ACTIVE");
                    return created;
                });
        membership.setStatus("ACTIVE");
        memberships.save(membership);

        return new ShellResult(user, org, me);
    }

    private void ensureSettings(UUID organizationId) {
        if (settings.findByOrganizationId(organizationId).isPresent()) return;
        OrganizationSettings created = new OrganizationSettings();
        created.setOrganizationId(organizationId);
        settings.save(created);
    }

    @Transactional
    public PlatformUser provisionPlatform(IamDtos.MeResult me) {
        if (me == null || me.userId() == null) {
            throw new IllegalArgumentException("IAM identity snapshot missing userId");
        }
        PlatformUser user = platformUsers.findByIamUserId(me.userId()).orElseGet(() -> {
            if (StringUtils.hasText(me.email())) {
                return platformUsers.findByEmailIgnoreCase(me.email()).orElseGet(PlatformUser::new);
            }
            return new PlatformUser();
        });
        if (user.getId() == null) {
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID() + ":iam-sso"));
            user.setActive(true);
        }
        user.setIamUserId(me.userId());
        user.setEmail(StringUtils.hasText(me.email()) ? me.email() : "iam-" + me.userId() + "@sankhya.local");
        String[] names = splitName(me.displayName());
        user.setFirstName(names[0]);
        user.setLastName(names[1]);
        return platformUsers.save(user);
    }

    private static String[] splitName(String displayName) {
        if (!StringUtils.hasText(displayName)) {
            return new String[] {"User", null};
        }
        String trimmed = displayName.trim();
        int space = trimmed.indexOf(' ');
        if (space < 0) return new String[] {trimmed, null};
        return new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }

    public record ShellResult(User user, Organization organization, IamDtos.MeResult me) {}
}
