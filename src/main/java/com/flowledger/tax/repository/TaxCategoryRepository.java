package com.flowledger.tax.repository;

import com.flowledger.tax.entity.TaxCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxCategoryRepository extends JpaRepository<TaxCategory, UUID> {
    Optional<TaxCategory> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<TaxCategory> findByOrganizationIdAndActiveTrue(UUID organizationId);

    List<TaxCategory> findByOrganizationId(UUID organizationId);

    Optional<TaxCategory> findByOrganizationIdAndCode(UUID organizationId, String code);

    @Query(
            """
        select case when count(c) > 0 then true else false end
        from TaxCategory c
        where c.organizationId = :organizationId
          and lower(trim(c.code)) = lower(trim(:code))
          and (:excludeId is null or c.id <> :excludeId)
        """)
    boolean existsByOrganizationIdAndCodeIgnoreCase(
            @Param("organizationId") UUID organizationId,
            @Param("code") String code,
            @Param("excludeId") UUID excludeId);
}
