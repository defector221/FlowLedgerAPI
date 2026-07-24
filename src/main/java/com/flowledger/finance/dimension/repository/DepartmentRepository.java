package com.flowledger.finance.dimension.repository;

import com.flowledger.finance.dimension.entity.Department;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    Optional<Department> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Department> findByOrganizationIdOrderByCodeAsc(UUID organizationId);

    boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
