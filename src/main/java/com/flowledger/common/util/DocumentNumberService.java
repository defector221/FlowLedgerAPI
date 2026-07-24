package com.flowledger.common.util;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentNumberService {
    private final DocumentSequenceRepository repo;

    public DocumentNumberService(DocumentSequenceRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public String next(UUID org, String type, String prefix, String template, String fyStart, LocalDate date) {
        return next(org, null, type, prefix, template, fyStart, date);
    }

    /** Branch-aware numbering; pass null branchId for organization-wide sequences. */
    @Transactional
    public String next(
            UUID org, UUID branchId, String type, String prefix, String template, String fyStart, LocalDate date) {
        String fy = FinancialYearUtil.financialYear(date, fyStart);
        DocumentSequence sequence = lockedOrCreate(org, branchId, type, fy, prefix);
        long nextValue = sequence.getNextValue();
        sequence.setNextValue(nextValue + 1);
        return format(sequence.getPrefix(), fy, template, nextValue);
    }

    @Transactional
    public void ensureNextAtLeast(
            UUID org, String type, String prefix, String fyStart, LocalDate date, long minNextValue) {
        ensureNextAtLeast(org, null, type, prefix, fyStart, date, minNextValue);
    }

    @Transactional
    public void ensureNextAtLeast(
            UUID org, UUID branchId, String type, String prefix, String fyStart, LocalDate date, long minNextValue) {
        if (minNextValue < 1) return;
        String fy = FinancialYearUtil.financialYear(date, fyStart);
        DocumentSequence sequence = lockedOrCreate(org, branchId, type, fy, prefix);
        if (sequence.getNextValue() < minNextValue) {
            sequence.setNextValue(minNextValue);
        }
    }

    private DocumentSequence lockedOrCreate(UUID org, UUID branchId, String type, String fy, String prefix) {
        return repo.findLocked(org, type, fy, branchId).orElseGet(() -> {
            DocumentSequence created = new DocumentSequence();
            created.setOrganizationId(org);
            created.setBranchId(branchId);
            created.setDocumentType(type);
            created.setFinancialYear(fy);
            created.setPrefix(prefix);
            return repo.saveAndFlush(created);
        });
    }

    private static String format(String prefix, String fy, String template, long nextValue) {
        return template.replace("{PREFIX}", prefix)
                .replace("{FY}", fy)
                .replaceAll(
                        "\\{SEQ:(\\d+)}",
                        String.format("%0" + template.replaceAll(".*\\{SEQ:(\\d+)}.*", "$1") + "d", nextValue));
    }
}
