package com.flowledger.accounting.repository;

import com.flowledger.accounting.entity.LedgerBalance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerBalanceRepository extends JpaRepository<LedgerBalance, UUID> {

    @Query(
            """
            select b from LedgerBalance b
            where b.organizationId = :org
              and b.accountId = :accountId
              and b.fiscalYearId = :fiscalYearId
              and ((:periodId is null and b.accountingPeriodId is null) or b.accountingPeriodId = :periodId)
            """)
    Optional<LedgerBalance> findBucket(
            @Param("org") UUID org,
            @Param("accountId") UUID accountId,
            @Param("fiscalYearId") UUID fiscalYearId,
            @Param("periodId") UUID periodId);
}
