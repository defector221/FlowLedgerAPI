package com.flowledger.finance.voucher.repository;

import com.flowledger.finance.voucher.entity.VoucherSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherSequenceRepository extends JpaRepository<VoucherSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select s from VoucherSequence s
            where s.organizationId = :org
              and s.voucherType = :type
              and s.financialYear = :fy
              and ((:branchId is null and s.branchId is null) or s.branchId = :branchId)
            """)
    Optional<VoucherSequence> findLocked(
            @Param("org") UUID org,
            @Param("type") String type,
            @Param("fy") String fy,
            @Param("branchId") UUID branchId);
}
