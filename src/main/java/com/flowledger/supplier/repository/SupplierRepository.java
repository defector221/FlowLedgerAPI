package com.flowledger.supplier.repository;

import com.flowledger.supplier.entity.Supplier;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface SupplierRepository extends JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {
    Optional<Supplier> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndSupplierCode(UUID organizationId, String supplierCode);
}
