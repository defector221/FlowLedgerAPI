package com.flowledger.product.repository;

import com.flowledger.product.entity.TaxRate;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {
    Optional<TaxRate> findByIdAndOrganizationId(UUID id, UUID org);

    boolean existsByOrganizationIdAndName(UUID org, String name);

    List<TaxRate> findByOrganizationIdAndActiveTrue(UUID org);
}
