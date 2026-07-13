package com.flowledger.warehouse.repository;

import com.flowledger.warehouse.entity.Warehouse;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    Optional<Warehouse> findByIdAndOrganizationId(UUID id, UUID org);

    List<Warehouse> findByOrganizationId(UUID org);

    boolean existsByOrganizationIdAndWarehouseCode(UUID org, String code);

    @Modifying
    @Query("update Warehouse w set w.defaultWarehouse=false where w.organizationId=:org")
    void clearDefault(UUID org);
}
