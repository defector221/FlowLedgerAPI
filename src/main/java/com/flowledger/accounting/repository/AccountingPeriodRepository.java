package com.flowledger.accounting.repository;

import com.flowledger.accounting.entity.AccountingPeriod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {
    List<AccountingPeriod> findByFiscalYearIdOrderByPeriodNumberAsc(UUID fiscalYearId);

    List<AccountingPeriod> findByOrganizationIdOrderByStartDateAsc(UUID organizationId);

    Optional<AccountingPeriod> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query(
            """
            select p from AccountingPeriod p
            where p.organizationId = :org
              and p.startDate <= :date and p.endDate >= :date
            """)
    Optional<AccountingPeriod> findCovering(@Param("org") UUID org, @Param("date") LocalDate date);
}
