package com.flowledger.inventory.repository;

import com.flowledger.inventory.entity.StockReservation;
import com.flowledger.inventory.entity.StockReservation.Status;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    Optional<StockReservation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<StockReservation> findByOrganizationIdAndReferenceTypeAndReferenceIdAndStatus(
            UUID organizationId, String referenceType, UUID referenceId, Status status);

    @Query(
            """
            select coalesce(sum(r.qty), 0) from StockReservation r
            where r.organizationId = :org
              and r.productId = :product
              and r.warehouseId = :warehouse
              and r.status = com.flowledger.inventory.entity.StockReservation.Status.ACTIVE
            """)
    BigDecimal activeReservedQty(
            @Param("org") UUID org, @Param("product") UUID product, @Param("warehouse") UUID warehouse);
}
