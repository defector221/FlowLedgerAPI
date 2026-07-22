package com.flowledger.transport.repository;

import com.flowledger.transport.entity.TransportCompany;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportCompanyRepository extends JpaRepository<TransportCompany, UUID> {
    List<TransportCompany> findByOrganizationIdAndDeletedFalseOrderByNameAsc(UUID organizationId);

    Optional<TransportCompany> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(UUID organizationId, String code);
}
