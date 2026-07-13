package com.flowledger.inventory.repository;

import com.flowledger.inventory.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    Optional<InventoryTransaction> findByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);

    @Query(value = """
            SELECT COALESCE(SUM(inward_qty) - SUM(outward_qty), 0)
            FROM inventory_transactions
            WHERE organization_id = :org
              AND product_id = :product
              AND (:warehouse IS NULL OR warehouse_id = :warehouse)
            """, nativeQuery = true)
    BigDecimal stockBalance(@Param("org") UUID org,
                            @Param("product") UUID product,
                            @Param("warehouse") UUID warehouse);

    List<InventoryTransaction> findByOrganizationIdAndProductIdAndWarehouseIdAndTransactionDateBetweenOrderByTransactionDateAscCreatedAtAsc(
            UUID org, UUID product, UUID warehouse, LocalDate from, LocalDate to);

    List<InventoryTransaction> findByOrganizationIdAndProductIdAndTransactionDateBetweenOrderByTransactionDateAscCreatedAtAsc(
            UUID org, UUID product, LocalDate from, LocalDate to);
}
