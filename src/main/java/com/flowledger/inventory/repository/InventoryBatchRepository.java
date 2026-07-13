package com.flowledger.inventory.repository;

import com.flowledger.inventory.entity.InventoryBatch;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, UUID> {
    Optional<InventoryBatch> findByOrganizationIdAndProductIdAndWarehouseIdAndBatchNumber(
            UUID org, UUID product, UUID warehouse, String batch);
}
