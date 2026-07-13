package com.flowledger.common.util;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentNumberService {
    private final DocumentSequenceRepository repo;

    public DocumentNumberService(DocumentSequenceRepository r) {
        repo = r;
    }

    @Transactional
    public String next(UUID org, String type, String prefix, String template, String fyStart, LocalDate date) {
        String fy = FinancialYearUtil.financialYear(date, fyStart);
        DocumentSequence s = repo.findLocked(org, type, fy).orElseGet(() -> {
            DocumentSequence n = new DocumentSequence();
            n.setOrganizationId(org);
            n.setDocumentType(type);
            n.setFinancialYear(fy);
            n.setPrefix(prefix);
            return repo.saveAndFlush(n);
        });
        long n = s.getNextValue();
        s.setNextValue(n + 1);
        return template.replace("{PREFIX}", s.getPrefix())
                .replace("{FY}", fy)
                .replaceAll(
                        "\\{SEQ:(\\d+)}",
                        String.format("%0" + template.replaceAll(".*\\{SEQ:(\\d+)}.*", "$1") + "d", n));
    }
}
