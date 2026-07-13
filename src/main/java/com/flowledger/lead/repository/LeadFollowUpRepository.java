package com.flowledger.lead.repository;

import com.flowledger.lead.entity.LeadFollowUp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadFollowUpRepository extends JpaRepository<LeadFollowUp, UUID> {
    List<LeadFollowUp> findByLeadIdAndOrganizationIdOrderByFollowUpAtAsc(UUID leadId, UUID organizationId);

    Optional<LeadFollowUp> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
