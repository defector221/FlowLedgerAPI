package com.flowledger.tax.repository;

import com.flowledger.tax.entity.TaxJurisdiction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxJurisdictionRepository extends JpaRepository<TaxJurisdiction, UUID> {
    Optional<TaxJurisdiction> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<TaxJurisdiction> findByOrganizationIdAndActiveTrue(UUID organizationId);

    List<TaxJurisdiction> findByOrganizationId(UUID organizationId);

    Optional<TaxJurisdiction> findByOrganizationIdAndCountryAndCode(UUID organizationId, String country, String code);
}
