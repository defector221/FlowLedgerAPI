package com.flowledger.organization.repository;

import com.flowledger.organization.entity.Branch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BranchRepository extends JpaRepository<Branch, UUID>, JpaSpecificationExecutor<Branch> {
    Optional<Branch> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Branch> findByOrganizationIdAndCode(UUID organizationId, String code);

    List<Branch> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    List<Branch> findByOrganizationIdAndActiveTrueOrderByNameAsc(UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);

    @Modifying
    @Query("update Branch b set b.defaultBranch = false where b.organizationId = :org")
    void clearDefault(UUID org);

    @Modifying
    @Query("update Branch b set b.headOffice = false where b.organizationId = :org")
    void clearHeadOffice(UUID org);
}
