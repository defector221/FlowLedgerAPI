package com.flowledger.location.repository;

import com.flowledger.location.entity.UserLocationAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserLocationAssignmentRepository extends JpaRepository<UserLocationAssignment, UUID> {
    List<UserLocationAssignment> findByOrganizationIdAndUserIdAndActiveTrue(UUID organizationId, UUID userId);
}
