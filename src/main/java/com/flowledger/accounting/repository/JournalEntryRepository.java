package com.flowledger.accounting.repository;

import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.domain.JournalStatus;
import com.flowledger.accounting.entity.JournalEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    Optional<JournalEntry> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<JournalEntry> findByOrganizationIdAndSourceAndReferenceId(
            UUID organizationId, JournalSource source, UUID referenceId);

    Page<JournalEntry> findByOrganizationIdOrderByEntryDateDescCreatedAtDesc(UUID organizationId, Pageable pageable);

    @Query(
            """
            select j from JournalEntry j
            where j.organizationId = :org
              and (:status is null or j.status = :status)
              and (:from is null or j.entryDate >= :from)
              and (:to is null or j.entryDate <= :to)
            order by j.entryDate desc, j.createdAt desc
            """)
    Page<JournalEntry> search(
            @Param("org") UUID org,
            @Param("status") JournalStatus status,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            Pageable pageable);

    @Query(
            """
            select count(j) from JournalEntry j
            where j.organizationId = :org and j.status = 'POSTED'
              and j.totalDebit <> j.totalCredit
            """)
    long countUnbalancedPosted(@Param("org") UUID org);

    List<JournalEntry> findByOrganizationIdAndStatusAndEntryDateBetween(
            UUID organizationId, JournalStatus status, LocalDate from, LocalDate to);
}
