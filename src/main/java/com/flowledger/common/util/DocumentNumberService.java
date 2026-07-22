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
        String fy = FinancialYearUtil.financialYear(date, fyStart);
        DocumentSequence sequence = repo.findLocked(org, type, fy).orElseGet(() -> {
            DocumentSequence created = new DocumentSequence();
            created.setOrganizationId(org);
            created.setDocumentType(type);
            created.setFinancialYear(fy);
            created.setPrefix(prefix);
            return repo.saveAndFlush(created);
        });
        long nextValue = sequence.getNextValue();
        sequence.setNextValue(nextValue + 1);
        return template.replace("{PREFIX}", sequence.getPrefix())
                .replace("{FY}", fy)
                .replaceAll(
                        "\\{SEQ:(\\d+)}",
                        String.format("%0" + template.replaceAll(".*\\{SEQ:(\\d+)}.*", "$1") + "d", nextValue));
    }
}
