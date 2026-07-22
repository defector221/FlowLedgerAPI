package com.flowledger.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Flat joined projection for party/account ledgers (avoids N+1 journal/account loads). */
public record LedgerLineView(
        UUID journalEntryId,
        String entryNumber,
        LocalDate entryDate,
        String description,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        UUID accountId,
        String accountCode,
        String accountName,
        UUID customerId,
        UUID supplierId,
        int lineNumber) {}
