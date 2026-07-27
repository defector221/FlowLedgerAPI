package com.flowledger.warehouse.repository;

import com.flowledger.warehouse.domain.WarehouseType;
import com.flowledger.warehouse.entity.Warehouse;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    Optional<Warehouse> findByIdAndOrganizationId(UUID id, UUID org);

    Optional<Warehouse> findFirstByOrganizationIdAndDefaultWarehouseTrue(UUID org);

    List<Warehouse> findByOrganizationId(UUID org);

    List<Warehouse> findByOrganizationIdAndWarehouseType(UUID org, WarehouseType warehouseType);

    List<Warehouse> findByOrganizationIdAndBranchId(UUID org, UUID branchId);

    List<Warehouse> findByOrganizationIdAndStoreId(UUID org, UUID storeId);

    boolean existsByOrganizationIdAndWarehouseCode(UUID org, String code);

    @Modifying
    @Query("update Warehouse w set w.defaultWarehouse=false where w.organizationId=:org")
    void clearDefault(UUID org);
}
