package com.flowledger.barcode.service;

import static com.flowledger.barcode.dto.ProductBarcodeDtos.*;

import com.flowledger.barcode.repository.ProductBarcodeHistoryRepository;
import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.pdf.HtmlDocumentPdfRenderer;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.retail.entity.RetailProductBarcode;
import com.flowledger.retail.repository.RetailProductBarcodeRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    private static final int BULK_LIMIT = 2000;

    private final RetailProductBarcodeRepository barcodes;
    private final ProductRepository products;
    private final BarcodeGeneratorService generator;
    private final BarcodeHistoryService history;
    private final ProductBarcodeHistoryRepository historyRepository;
    private final HtmlDocumentPdfRenderer pdfRenderer;

    public ProductBarcodeManagementService(
            RetailProductBarcodeRepository barcodes,
            ProductRepository products,
            BarcodeGeneratorService generator,
            BarcodeHistoryService history,
            ProductBarcodeHistoryRepository historyRepository,
            HtmlDocumentPdfRenderer pdfRenderer) {
        this.barcodes = barcodes;
        this.products = products;
        this.generator = generator;
        this.history = history;
        this.historyRepository = historyRepository;
        this.pdfRenderer = pdfRenderer;
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
        return historyRepository.findByOrganizationIdAndProductIdOrderByCreatedAtDesc(orgId(), productId).stream()
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
        if (primary) {
            syncProductBarcode(product, value);
        }
        history.record(
                orgId(), productId, saved.getId(), null, value, "GENERATED", request == null ? null : request.reason());
        return toResponse(saved);
    }

    public BarcodeResponse regenerate(UUID productId, RegenerateBarcodeRequest request) {
        Product product = requireProduct(productId);
        RetailProductBarcode current =
                barcodes.findByOrganizationIdAndProductIdAndPrimaryTrueAndDeletedAtIsNull(orgId(), productId).stream()
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
        syncProductBarcode(product, newValue);
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
        RetailProductBarcode row = barcodes.findByIdAndOrganizationIdAndProductIdAndDeletedAtIsNull(
                        barcodeId, orgId(), productId)
                .orElseThrow(() -> notFound("Barcode not found"));
        softDelete(row, reason);
    }

    public BulkGenerateResultResponse bulkGenerate(BulkGenerateRequest request) {
        List<Product> targets = resolveProducts(request == null ? null : request.productIds(), true);
        int generated = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (Product product : targets) {
            try {
                if (!barcodes.findByOrganizationIdAndProductIdAndPrimaryTrueAndDeletedAtIsNull(orgId(), product.getId())
                        .isEmpty()) {
                    skipped++;
                    continue;
                }
                if (product.getBarcode() != null && !product.getBarcode().isBlank()) {
                    skipped++;
                    continue;
                }
                generate(product.getId(), new GenerateBarcodeRequest("EAN13", true, "bulk-generate"));
                generated++;
            } catch (Exception ex) {
                failed++;
                String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                errors.add(product.getSku() + ": " + msg);
                if (errors.size() > 25) {
                    errors.add("…additional errors omitted");
                    break;
                }
            }
        }
        return new BulkGenerateResultResponse(targets.size(), generated, skipped, failed, errors);
    }

    @Transactional(readOnly = true)
    public byte[] downloadSheet(BulkGenerateRequest request) {
        List<BarcodeSheetItem> items = resolveSheetItems(request == null ? null : request.productIds());
        if (items.isEmpty()) {
            throw badRequest("No barcodes available to download");
        }
        StringBuilder cards = new StringBuilder();
        for (BarcodeSheetItem item : items) {
            String png = encodeBarcodePngBase64(item.barcode(), item.barcodeType());
            cards.append(
                    """
                    <div class="card">
                      <div class="name">%s</div>
                      <div class="sku">%s</div>
                      <img src="data:image/png;base64,%s" alt="%s"/>
                      <div class="code">%s</div>
                    </div>
                    """
                            .formatted(
                                    escapeHtml(item.name()),
                                    escapeHtml(item.sku()),
                                    png,
                                    escapeHtml(item.barcode()),
                                    escapeHtml(item.barcode())));
        }
        String html =
                """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8"/>
                  <style>
                    @page { size: A4; margin: 10mm; }
                    body { font-family: Helvetica, Arial, sans-serif; margin: 0; color: #0f172a; }
                    .grid { display: block; }
                    .card {
                      display: inline-block;
                      vertical-align: top;
                      width: 48%%;
                      box-sizing: border-box;
                      border: 1px solid #e2e8f0;
                      border-radius: 6px;
                      padding: 8px;
                      margin: 0 1%% 10px 0;
                      text-align: center;
                      page-break-inside: avoid;
                    }
                    .name { font-size: 10pt; font-weight: bold; margin-bottom: 2px; }
                    .sku, .code { font-size: 8pt; color: #475569; }
                    img { max-width: 100%%; height: 48px; margin: 6px 0; }
                  </style>
                </head>
                <body><div class="grid">%s</div></body>
                </html>
                """
                        .formatted(cards);
        return pdfRenderer.render(html, null);
    }

    private void softDelete(RetailProductBarcode row, String reason) {
        row.setDeletedAt(OffsetDateTime.now());
        row.setStatus(STATUS_INACTIVE);
        row.setPrimary(false);
        audit(row, false);
        barcodes.save(row);
        history.record(orgId(), row.getProductId(), row.getId(), row.getBarcode(), null, "DELETED", reason);
        if (barcodes.findByOrganizationIdAndProductIdAndPrimaryTrueAndDeletedAtIsNull(orgId(), row.getProductId())
                .isEmpty()) {
            barcodes
                    .findByOrganizationIdAndProductIdAndDeletedAtIsNullOrderByPrimaryDescCreatedAtAsc(
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

    private void syncProductBarcode(Product product, String barcode) {
        product.setBarcode(barcode);
        TenantContext.userId().ifPresent(product::setUpdatedBy);
        products.save(product);
    }

    private List<Product> resolveProducts(List<UUID> productIds, boolean forGenerate) {
        List<Product> all;
        if (productIds == null || productIds.isEmpty()) {
            all = products.findByOrganizationIdAndActiveTrue(orgId());
        } else {
            all = products.findAllById(productIds).stream()
                    .filter(p -> orgId().equals(p.getOrganizationId()))
                    .toList();
        }
        if (all.size() > BULK_LIMIT) {
            throw badRequest("Too many products (max " + BULK_LIMIT + ")");
        }
        if (forGenerate) {
            return all.stream()
                    .sorted(Comparator.comparing(Product::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
        }
        return all;
    }

    private List<BarcodeSheetItem> resolveSheetItems(List<UUID> productIds) {
        List<Product> targets = resolveProducts(productIds, false);
        List<BarcodeSheetItem> items = new ArrayList<>();
        for (Product product : targets) {
            RetailProductBarcode primary =
                    barcodes
                            .findByOrganizationIdAndProductIdAndPrimaryTrueAndDeletedAtIsNull(orgId(), product.getId())
                            .stream()
                            .findFirst()
                            .orElse(null);
            String value = primary != null
                    ? primary.getBarcode()
                    : (product.getBarcode() == null || product.getBarcode().isBlank() ? null : product.getBarcode());
            if (value == null) {
                continue;
            }
            String type = primary != null && primary.getBarcodeType() != null ? primary.getBarcodeType() : "CODE128";
            items.add(new BarcodeSheetItem(
                    product.getId(),
                    Objects.toString(product.getSku(), ""),
                    Objects.toString(product.getName(), ""),
                    value,
                    type));
        }
        items.sort(Comparator.comparing(BarcodeSheetItem::name, String.CASE_INSENSITIVE_ORDER));
        return items;
    }

    private String encodeBarcodePngBase64(String value, String barcodeType) {
        try {
            BarcodeFormat format = resolveFormat(barcodeType);
            BitMatrix matrix = new MultiFormatWriter().encode(value, format, 360, 90);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception primary) {
            try {
                BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.CODE_128, 360, 90);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                MatrixToImageWriter.writeToStream(matrix, "PNG", out);
                return Base64.getEncoder().encodeToString(out.toByteArray());
            } catch (Exception fallback) {
                throw badRequest("Unable to render barcode: " + value);
            }
        }
    }

    private static BarcodeFormat resolveFormat(String barcodeType) {
        String normalized = barcodeType == null ? "CODE128" : barcodeType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "EAN13" -> BarcodeFormat.EAN_13;
            case "EAN8" -> BarcodeFormat.EAN_8;
            case "UPC", "UPCA" -> BarcodeFormat.UPC_A;
            case "UPCE" -> BarcodeFormat.UPC_E;
            case "CODE39" -> BarcodeFormat.CODE_39;
            case "ITF14" -> BarcodeFormat.ITF;
            default -> BarcodeFormat.CODE_128;
        };
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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
