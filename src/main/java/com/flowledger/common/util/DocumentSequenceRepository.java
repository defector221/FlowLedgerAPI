package com.flowledger.common.util;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select d from DocumentSequence d
            where d.organizationId = :org
              and d.documentType = :type
              and d.financialYear = :fy
              and ((:branchId is null and d.branchId is null) or d.branchId = :branchId)
            """)
    Optional<DocumentSequence> findLocked(
            @Param("org") UUID org,
            @Param("type") String type,
            @Param("fy") String fy,
            @Param("branchId") UUID branchId);
}
