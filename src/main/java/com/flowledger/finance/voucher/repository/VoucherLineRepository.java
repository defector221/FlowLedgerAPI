package com.flowledger.finance.voucher.repository;

import com.flowledger.finance.voucher.entity.VoucherLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherLineRepository extends JpaRepository<VoucherLine, UUID> {
    List<VoucherLine> findByVoucherIdOrderBySortOrderAsc(UUID voucherId);

    void deleteByVoucherId(UUID voucherId);
}
