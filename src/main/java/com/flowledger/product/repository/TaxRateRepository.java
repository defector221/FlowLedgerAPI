package com.flowledger.product.repository;

import com.flowledger.product.entity.TaxRate;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {
    Optional<TaxRate> findByIdAndOrganizationId(UUID id, UUID org);

    @Query(
            """
            select case when count(t) > 0 then true else false end
            from TaxRate t
            where t.organizationId = :organizationId
              and lower(trim(t.name)) = lower(trim(:name))
            """)
    boolean existsByOrganizationIdAndNameIgnoreCase(
            @Param("organizationId") UUID organizationId, @Param("name") String name);

    List<TaxRate> findByOrganizationIdAndActiveTrue(UUID org);
}
