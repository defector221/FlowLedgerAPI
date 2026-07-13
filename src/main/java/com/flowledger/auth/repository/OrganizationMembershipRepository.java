package com.flowledger.auth.repository;

import com.flowledger.auth.entity.OrganizationMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {
    Optional<OrganizationMembership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    List<OrganizationMembership> findByUserId(UUID userId);

    List<OrganizationMembership> findByOrganizationId(UUID organizationId);

    List<OrganizationMembership> findByUserIdAndStatus(UUID userId, String status);

    Optional<OrganizationMembership> findByInvitationToken(String invitationToken);

    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}
