package com.flowledger.retail.repository;

import com.flowledger.retail.entity.PosSalePayment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosSalePaymentRepository extends JpaRepository<PosSalePayment, UUID> {
    List<PosSalePayment> findByOrganizationIdAndPosSaleId(UUID organizationId, UUID posSaleId);

    void deleteByPosSaleId(UUID posSaleId);
}
