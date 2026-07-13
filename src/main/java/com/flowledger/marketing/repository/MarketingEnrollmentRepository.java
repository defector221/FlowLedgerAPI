package com.flowledger.marketing.repository;

import com.flowledger.marketing.entity.MarketingEnrollment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketingEnrollmentRepository extends JpaRepository<MarketingEnrollment, UUID> {
    Optional<MarketingEnrollment> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsBySequenceIdAndRecipientTypeAndRecipientIdAndStatus(
            UUID sequenceId, String recipientType, UUID recipientId, String status);

    @Query("select e from MarketingEnrollment e where e.status = 'ACTIVE'")
    List<MarketingEnrollment> findActiveEnrollments();
}
