package com.flowledger.inventory.repository;
import com.flowledger.inventory.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch,UUID> {
 Optional<InventoryBatch> findByOrganizationIdAndProductIdAndWarehouseIdAndBatchNumber(UUID org,UUID product,UUID warehouse,String batch);
}
