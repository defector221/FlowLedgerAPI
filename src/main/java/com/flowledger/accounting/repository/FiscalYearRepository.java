package com.flowledger.accounting.repository;

import com.flowledger.accounting.entity.FiscalYear;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FiscalYearRepository extends JpaRepository<FiscalYear, UUID> {
    List<FiscalYear> findByOrganizationIdOrderByStartDateDesc(UUID organizationId);

    Optional<FiscalYear> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<FiscalYear> findByOrganizationIdAndCurrentTrue(UUID organizationId);

    @Query(
            """
            select f from FiscalYear f
            where f.organizationId = :org
              and f.startDate <= :date and f.endDate >= :date
            """)
    Optional<FiscalYear> findCovering(@Param("org") UUID org, @Param("date") LocalDate date);

    @Query(
            """
            select count(f) > 0 from FiscalYear f
            where f.organizationId = :org
              and f.startDate <= :endDate and f.endDate >= :startDate
              and (:excludeId is null or f.id <> :excludeId)
            """)
    boolean existsOverlapping(
            @Param("org") UUID org,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") UUID excludeId);
}
