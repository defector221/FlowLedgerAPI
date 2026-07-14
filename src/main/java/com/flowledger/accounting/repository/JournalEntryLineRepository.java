package com.flowledger.accounting.repository;

import com.flowledger.accounting.entity.JournalEntryLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, UUID> {
    List<JournalEntryLine> findByJournalEntryIdOrderByLineNumberAsc(UUID journalEntryId);

    void deleteByJournalEntryId(UUID journalEntryId);

    @Query(
            """
            select l from JournalEntryLine l, JournalEntry j
            where j.id = l.journalEntryId
              and l.organizationId = :org
              and l.accountId = :accountId
              and j.status = com.flowledger.accounting.domain.JournalStatus.POSTED
              and (:from is null or j.entryDate >= :from)
              and (:to is null or j.entryDate <= :to)
            order by j.entryDate, l.lineNumber
            """)
    List<JournalEntryLine> findPostedForAccount(
            @Param("org") UUID org,
            @Param("accountId") UUID accountId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);

    @Query(
            """
            select l from JournalEntryLine l, JournalEntry j
            where j.id = l.journalEntryId
              and l.organizationId = :org
              and l.customerId = :customerId
              and j.status = com.flowledger.accounting.domain.JournalStatus.POSTED
              and (:from is null or j.entryDate >= :from)
              and (:to is null or j.entryDate <= :to)
            order by j.entryDate, l.lineNumber
            """)
    List<JournalEntryLine> findPostedForCustomer(
            @Param("org") UUID org,
            @Param("customerId") UUID customerId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);

    @Query(
            """
            select l from JournalEntryLine l, JournalEntry j
            where j.id = l.journalEntryId
              and l.organizationId = :org
              and l.supplierId = :supplierId
              and j.status = com.flowledger.accounting.domain.JournalStatus.POSTED
              and (:from is null or j.entryDate >= :from)
              and (:to is null or j.entryDate <= :to)
            order by j.entryDate, l.lineNumber
            """)
    List<JournalEntryLine> findPostedForSupplier(
            @Param("org") UUID org,
            @Param("supplierId") UUID supplierId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);
}
