package com.flowledger.inventory.repository;

import com.flowledger.inventory.entity.InventoryCostLayer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryCostLayerRepository extends JpaRepository<InventoryCostLayer, UUID> {

    @Query(
            """
            select l from InventoryCostLayer l
            where l.organizationId = :org
              and l.productId = :product
              and l.warehouseId = :warehouse
              and l.qtyRemaining > 0
            order by l.receivedAt asc, l.createdAt asc
            """)
    List<InventoryCostLayer> findOpenLayersFifo(
            @Param("org") UUID org, @Param("product") UUID product, @Param("warehouse") UUID warehouse);

    @Query(
            """
            select l from InventoryCostLayer l
            where l.organizationId = :org
              and l.productId = :product
              and l.warehouseId = :warehouse
              and l.qtyRemaining > 0
            order by l.receivedAt asc, l.createdAt asc
            """)
    List<InventoryCostLayer> findOpenLayers(
            @Param("org") UUID org, @Param("product") UUID product, @Param("warehouse") UUID warehouse);
}
