package com.flowledger.barcode.service;

import static com.flowledger.barcode.dto.ScanHistoryDtos.Response;

import com.flowledger.barcode.repository.ScanHistoryRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ScanHistoryService {
    private final ScanHistoryRepository repository;

    public ScanHistoryService(ScanHistoryRepository repository) {
        this.repository = repository;
    }

    public Page<Response> list(Pageable pageable) {
        return repository
                .findByOrganizationIdOrderByScannedAtDesc(org(), pageable)
                .map(row -> new Response(
                        row.getId(),
                        row.getBarcode(),
                        row.getSource(),
                        row.getModule(),
                        row.getResolvedProductId(),
                        row.getResolvedVariantId(),
                        row.isSuccess(),
                        row.getFailureReason(),
                        row.getScannedAt(),
                        row.getCreatedBy()));
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }
}
