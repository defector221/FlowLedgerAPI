package com.flowledger.barcode.service;

import static com.flowledger.barcode.dto.ProductBarcodeDtos.*;

import com.flowledger.barcode.repository.ProductBarcodeHistoryRepository;
import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.product.entity.Product;
import com.flowledger.product.entity.ProductImportJob;
import com.flowledger.product.repository.ProductImportJobRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.retail.entity.RetailProductBarcode;
import com.flowledger.retail.repository.RetailProductBarcodeRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProductBarcodeManagementService extends OrganizationScopedService {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private final RetailProductBarcodeRepository barcodes;
    private final ProductRepository products;
    private final BarcodeGeneratorService generator;
    private final BarcodeHistoryService history;
    private final ProductBarcodeHistoryRepository historyRepository;
    private final ProductImportJobRepository importJobs;

    public ProductBarcodeManagementService(
            RetailProductBarcodeRepository barcodes,
            ProductRepository products,
            BarcodeGeneratorService generator,
            BarcodeHistoryService history,
            ProductBarcodeHistoryRepository historyRepository,
            ProductImportJobRepository importJobs) {
        this.barcodes = barcodes;
        this.products = products;
        this.generator = generator;
        this.history = history;
        this.historyRepository = historyRepository;
        this.importJobs = importJobs;
    }

    @Transactional(readOnly = true)
    public List<BarcodeResponse> list(UUID productId) {
        requireProduct(productId);
        return barcodes
                .findByOrganizationIdAndProductIdAndDeletedAtIsNullOrderByPrimaryDescCreatedAtAsc(orgId(), productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BarcodeHistoryResponse> history(UUID productId) {
        requireProduct(productId);
        return historyRepository
                .findByOrganizationIdAndProductIdOrderByCreatedAtDesc(orgId(), productId)
                .stream()
                .map(h -> new BarcodeHistoryResponse(
                        h.getId(),
                        h.getProductId(),
                        h.getBarcodeId(),
                        h.getOldBarcode(),
                        h.getNewBarcode(),
                        h.getOperation(),
                        h.getReason(),
                        h.getCreatedBy(),
                        h.getCreatedAt()))
                .toList();
    }

    public BarcodeResponse create(UUID productId, CreateBarcodeRequest request) {
        requireProduct(productId);
        String value = normalizeBarcode(request.barcode());
        ensureUnique(value, null);
        boolean primary = Boolean.TRUE.equals(request.primary());
        if (primary) {
            clearPrimary(productId, null);
        } else if (barcodes.findByOrganizationIdAndProductIdAndPrimaryTrueAndDeletedAtIsNull(orgId(), productId)
                .isEmpty()) {
            primary = true;
        }
        RetailProductBarcode row = new RetailProductBarcode();
        row.setOrganizationId(orgId());
        row.setProductId(productId);
        row.setVariantId(request.variantId());
        row.setBarcode(value);
        row.setBarcodeType(request.barcodeType() == null ? "EAN13" : request.barcodeType());
        row.setPrimary(primary);
        row.setStatus(STATUS_ACTIVE);
        row.setGenerated(false);
        audit(row, true);
        RetailProductBarcode saved = barcodes.save(row);
        history.record(orgId(), productId, saved.getId(), null, value, "ALIAS_ADDED", null);
        return toResponse(saved);
    }

    public BarcodeResponse generate(UUID productId, GenerateBarcodeRequest request) {
        Product product = requireProduct(productId);
        String value = generator.generateEan13(orgId(), productId, product.getSku());
        boolean primary = request == null || request.primary() == null || request.primary();
        if (primary) {
            clearPrimary(productId, null);
        }
        RetailProductBarcode row = new RetailProductBarcode();
        row.setOrganizationId(orgId());
        row.setProductId(productId);
        row.setBarcode(value);
        row.setBarcodeType(request != null && request.barcodeType() != null ? request.barcodeType() : "EAN13");
        row.setPrimary(primary);
        row.setStatus(STATUS_ACTIVE);
        row.setGenerated(true);
        audit(row, true);
        RetailProductBarcode saved = barcodes.save(row);
        history.record(
                orgId(),
                productId,
                saved.getId(),
                null,
                value,
                "GENERATED",
                request == null ? null : request.reason());
        return toResponse(saved);
    }

    public BarcodeResponse regenerate(UUID productId, RegenerateBarcodeRequest request) {
        Product product = requireProduct(productId);
        RetailProductBarcode current = barcodes
                .findByOrganizationIdAndProductIdAndPrimaryTrueAndDeletedAtIsNull(orgId(), productId)
                .stream()
                .findFirst()
                .orElseThrow(() -> notFound("Primary barcode not found"));
        String oldValue = current.getBarcode();
        softDelete(current, request == null ? null : request.reason());
        String newValue = generator.generateEan13(orgId(), productId, product.getSku());
        RetailProductBarcode row = new RetailProductBarcode();
        row.setOrganizationId(orgId());
        row.setProductId(productId);
        row.setBarcode(newValue);
        row.setBarcodeType(current.getBarcodeType());
        row.setPrimary(true);
        row.setStatus(STATUS_ACTIVE);
        row.setGenerated(true);
        audit(row, true);
        RetailProductBarcode saved = barcodes.save(row);
        history.record(
                orgId(),
                productId,
                saved.getId(),
                oldValue,
                newValue,
                "REGENERATED",
                request == null ? null : request.reason());
        return toResponse(saved);
    }

    public void softDelete(UUID productId, UUID barcodeId, String reason) {
        requireProduct(productId);
        RetailProductBarcode row = barcodes
                .findByIdAndOrganizationIdAndProductIdAndDeletedAtIsNull(barcodeId, orgId(), productId)
                .orElseThrow(() -> notFound("Barcode not found"));
        softDelete(row, reason);
    }

    public BulkGenerateJobResponse bulkGenerate(BulkGenerateRequest request) {
        ProductImportJob job = new ProductImportJob();
        job.setOrganizationId(orgId());
        job.setStatus("PENDING");
        job.setFileName("bulk-barcode-generate");
        TenantContext.userId().ifPresent(job::setCreatedBy);
        ProductImportJob saved = importJobs.save(job);
        return new BulkGenerateJobResponse(saved.getId(), saved.getStatus());
    }

    private void softDelete(RetailProductBarcode row, String reason) {
        row.setDeletedAt(OffsetDateTime.now());
        row.setStatus(STATUS_INACTIVE);
        row.setPrimary(false);
        audit(row, false);
        barcodes.save(row);
        history.record(
                orgId(),
                row.getProductId(),
                row.getId(),
                row.getBarcode(),
                null,
                "DELETED",
                reason);
        if (barcodes.findByOrganizationIdAndProductIdAndPrimaryTrueAndDeletedAtIsNull(
                        orgId(), row.getProductId())
                .isEmpty()) {
            barcodes.findByOrganizationIdAndProductIdAndDeletedAtIsNullOrderByPrimaryDescCreatedAtAsc(
                            orgId(), row.getProductId())
                    .stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setPrimary(true);
                        audit(next, false);
                        barcodes.save(next);
                    });
        }
    }

    private void clearPrimary(UUID productId, UUID excludeId) {
        for (RetailProductBarcode existing :
                barcodes.findByOrganizationIdAndProductIdAndPrimaryTrueAndDeletedAtIsNull(orgId(), productId)) {
            if (excludeId != null && excludeId.equals(existing.getId())) {
                continue;
            }
            existing.setPrimary(false);
            audit(existing, false);
            barcodes.save(existing);
        }
    }

    private void ensureUnique(String barcode, UUID excludeId) {
        barcodes.findActiveByOrganizationIdAndBarcode(orgId(), barcode).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
                throw conflict("Barcode already exists");
            }
        });
    }

    private Product requireProduct(UUID productId) {
        return required(products.findByIdAndOrganizationId(productId, orgId()), "Product");
    }

    private String normalizeBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw badRequest("barcode is required");
        }
        return barcode.trim();
    }

    private void audit(RetailProductBarcode row, boolean created) {
        TenantContext.userId().ifPresent(user -> {
            if (created) {
                row.setCreatedBy(user);
            }
            row.setUpdatedBy(user);
        });
    }

    private BarcodeResponse toResponse(RetailProductBarcode row) {
        return new BarcodeResponse(
                row.getId(),
                row.getProductId(),
                row.getVariantId(),
                row.getBarcode(),
                row.getBarcodeType(),
                row.isPrimary(),
                row.getStatus(),
                row.isGenerated(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
