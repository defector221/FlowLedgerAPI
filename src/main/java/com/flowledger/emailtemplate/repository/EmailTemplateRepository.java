package com.flowledger.emailtemplate.repository;

import com.flowledger.emailtemplate.entity.EmailTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {
    List<EmailTemplate> findByOrganizationIdAndActiveTrueOrderByUpdatedAtDesc(UUID organizationId);

    Optional<EmailTemplate> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndActiveTrue(UUID organizationId, String name);

    boolean existsByOrganizationIdAndNameIgnoreCaseAndActiveTrueAndIdNot(
            UUID organizationId, String name, UUID id);
}
