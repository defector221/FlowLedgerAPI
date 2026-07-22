package com.flowledger.accounting.repository;

import com.flowledger.accounting.dto.LedgerLineView;
import com.flowledger.accounting.entity.JournalEntryLine;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, UUID> {
    List<JournalEntryLine> findByJournalEntryIdOrderByLineNumberAsc(UUID journalEntryId);

    void deleteByJournalEntryId(UUID journalEntryId);

    boolean existsByOrganizationIdAndAccountId(UUID organizationId, UUID accountId);

    @Query(
            """
            select new com.flowledger.accounting.dto.LedgerLineView(
                j.id,
                j.entryNumber,
                j.entryDate,
                coalesce(l.description, j.description),
                l.debitAmount,
                l.creditAmount,
                l.accountId,
                a.accountCode,
                a.accountName,
                l.customerId,
                l.supplierId,
                l.lineNumber
            )
            from JournalEntryLine l
            join JournalEntry j on j.id = l.journalEntryId
            left join Account a on a.id = l.accountId
            where l.organizationId = :org
              and l.accountId = :accountId
              and j.status = com.flowledger.accounting.domain.JournalStatus.POSTED
              and j.entryDate >= coalesce(:from, j.entryDate)
              and j.entryDate <= coalesce(:to, j.entryDate)
            order by j.entryDate, l.lineNumber
            """)
    List<LedgerLineView> findPostedLedgerForAccount(
            @Param("org") UUID org,
            @Param("accountId") UUID accountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(
            """
            select new com.flowledger.accounting.dto.LedgerLineView(
                j.id,
                j.entryNumber,
                j.entryDate,
                coalesce(l.description, j.description),
                l.debitAmount,
                l.creditAmount,
                l.accountId,
                a.accountCode,
                a.accountName,
                l.customerId,
                l.supplierId,
                l.lineNumber
            )
            from JournalEntryLine l
            join JournalEntry j on j.id = l.journalEntryId
            left join Account a on a.id = l.accountId
            where l.organizationId = :org
              and l.customerId = :customerId
              and j.status = com.flowledger.accounting.domain.JournalStatus.POSTED
              and j.entryDate >= coalesce(:from, j.entryDate)
              and j.entryDate <= coalesce(:to, j.entryDate)
            order by j.entryDate, l.lineNumber
            """)
    List<LedgerLineView> findPostedLedgerForCustomer(
            @Param("org") UUID org,
            @Param("customerId") UUID customerId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(
            """
            select new com.flowledger.accounting.dto.LedgerLineView(
                j.id,
                j.entryNumber,
                j.entryDate,
                coalesce(l.description, j.description),
                l.debitAmount,
                l.creditAmount,
                l.accountId,
                a.accountCode,
                a.accountName,
                l.customerId,
                l.supplierId,
                l.lineNumber
            )
            from JournalEntryLine l
            join JournalEntry j on j.id = l.journalEntryId
            left join Account a on a.id = l.accountId
            where l.organizationId = :org
              and l.supplierId = :supplierId
              and j.status = com.flowledger.accounting.domain.JournalStatus.POSTED
              and j.entryDate >= coalesce(:from, j.entryDate)
              and j.entryDate <= coalesce(:to, j.entryDate)
            order by j.entryDate, l.lineNumber
            """)
    List<LedgerLineView> findPostedLedgerForSupplier(
            @Param("org") UUID org,
            @Param("supplierId") UUID supplierId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
