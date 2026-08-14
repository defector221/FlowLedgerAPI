package com.flowledger.organization.repository;

import com.flowledger.organization.entity.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByNameIgnoreCase(String name);

    long countByLifecycleStatus(String lifecycleStatus);

    long countByActiveTrue();

    @Query(
            """
            SELECT o FROM Organization o
            WHERE (:q IS NULL OR :q = '' OR LOWER(o.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(o.email, '')) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Organization> search(@Param("q") String q, Pageable pageable);

    Optional<Organization> findByIamOrganizationId(UUID iamOrganizationId);
}
