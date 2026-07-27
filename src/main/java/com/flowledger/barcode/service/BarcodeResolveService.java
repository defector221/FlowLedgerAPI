package com.flowledger.barcode.service;

import static com.flowledger.barcode.dto.BarcodeDtos.*;
import static com.flowledger.retail.dto.RetailDtos.ProductLookupResponse;

import com.flowledger.barcode.entity.ScanHistory;
import com.flowledger.barcode.repository.ScanHistoryRepository;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.product.entity.Product;
import com.flowledger.product.entity.SupplierCatalogItem;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.product.repository.SupplierCatalogItemRepository;
import com.flowledger.retail.entity.RetailProductBarcode;
import com.flowledger.retail.entity.RetailProductVariant;
import com.flowledger.retail.repository.RetailProductBarcodeRepository;
import com.flowledger.retail.repository.RetailProductVariantRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class BarcodeResolveService {
    private final ScanHistoryRepository scanHistory;
    private final RetailProductBarcodeRepository barcodes;
    private final RetailProductVariantRepository variants;
    private final ProductRepository products;
    private final SupplierCatalogItemRepository supplierCatalog;

    public BarcodeResolveService(
            ScanHistoryRepository scanHistory,
            RetailProductBarcodeRepository barcodes,
            RetailProductVariantRepository variants,
            ProductRepository products,
            SupplierCatalogItemRepository supplierCatalog) {
        this.scanHistory = scanHistory;
        this.barcodes = barcodes;
        this.variants = variants;
        this.products = products;
        this.supplierCatalog = supplierCatalog;
    }

    @Transactional(readOnly = true)
    public ScanResolveResponse resolve(ScanResolveRequest request) {
        String trimmed = request.barcode().trim();
        if (trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "barcode is required");
        }
        try {
            ResolvedMatch match = resolveInternal(trimmed);
            logScan(trimmed, request.source(), request.module(), match, true, null);
            return new ScanResolveResponse(
                    true,
                    trimmed,
                    match.matchType(),
                    match.product(),
                    null);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                logScan(trimmed, request.source(), request.module(), null, false, ex.getReason());
                return new ScanResolveResponse(false, trimmed, null, null, ex.getReason());
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ProductLookupResponse lookupByBarcode(String barcode) {
        return resolveInternal(barcode.trim()).product();
    }

    @Transactional(readOnly = true)
    public Optional<ProductLookupResponse> lookupByBarcodeOptional(String barcode) {
        try {
            return Optional.of(lookupByBarcode(barcode));
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private ResolvedMatch resolveInternal(String barcode) {
        UUID org = org();

        Optional<RetailProductBarcode> mapping = barcodes.findActiveByOrganizationIdAndBarcode(org, barcode);
        if (mapping.isPresent()) {
            RetailProductBarcode m = mapping.get();
            if (m.getVariantId() != null) {
                Optional<RetailProductVariant> v =
                        variants.findByIdAndOrganizationIdAndDeletedFalse(m.getVariantId(), org);
                if (v.isPresent()) {
                    return new ResolvedMatch("RETAIL_ALIAS", fromVariant(v.get(), barcode));
                }
            }
            if (m.getProductId() != null) {
                return products.findByIdAndOrganizationId(m.getProductId(), org)
                        .map(p -> new ResolvedMatch("RETAIL_ALIAS", fromProduct(p, barcode)))
                        .orElseThrow(() -> notFound("Product not found for barcode"));
            }
        }

        Optional<RetailProductVariant> variant =
                variants.findFirstByOrganizationIdAndBarcodeAndDeletedFalse(org, barcode);
        if (variant.isPresent()) {
            return new ResolvedMatch("VARIANT_BARCODE", fromVariant(variant.get(), barcode));
        }

        Optional<Product> byBarcode = products.findFirstByOrganizationIdAndBarcode(org, barcode);
        if (byBarcode.isPresent()) {
            return new ResolvedMatch("PRODUCT_BARCODE", fromProduct(byBarcode.get(), barcode));
        }

        Optional<SupplierCatalogItem> supplierItem =
                supplierCatalog.findFirstByOrganizationIdAndSupplierSkuIgnoreCaseAndActiveTrueAndDeletedFalse(
                        org, barcode);
        if (supplierItem.isPresent()) {
            SupplierCatalogItem item = supplierItem.get();
            Product p = products.findByIdAndOrganizationId(item.getProductId(), org)
                    .orElseThrow(() -> notFound("Product not found for supplier code"));
            return new ResolvedMatch("SUPPLIER_CODE", fromProduct(p, barcode));
        }

        String needle = barcode.toLowerCase(Locale.ROOT);
        List<Product> active = products.findByOrganizationIdAndActiveTrue(org);
        Optional<Product> exact = active.stream()
                .filter(p -> (p.getName() != null && p.getName().equalsIgnoreCase(barcode))
                        || (p.getSku() != null && p.getSku().equalsIgnoreCase(barcode)))
                .findFirst();
        if (exact.isPresent()) {
            Product p = exact.get();
            return new ResolvedMatch(
                    "SKU_OR_NAME",
                    fromProduct(p, p.getBarcode() != null ? p.getBarcode() : barcode));
        }
        Optional<Product> partial = active.stream()
                .filter(p -> (p.getName() != null
                                && p.getName().toLowerCase(Locale.ROOT).contains(needle))
                        || (p.getSku() != null
                                && p.getSku().toLowerCase(Locale.ROOT).contains(needle)))
                .findFirst();
        return partial
                .map(p -> new ResolvedMatch(
                        "SKU_OR_NAME_PARTIAL",
                        fromProduct(p, p.getBarcode() != null ? p.getBarcode() : barcode)))
                .orElseThrow(() -> notFound("No product found for \"" + barcode + "\""));
    }

    private ProductLookupResponse fromProduct(Product p, String barcode) {
        return new ProductLookupResponse(
                p.getId(),
                null,
                p.getName(),
                barcode,
                p.getSellingPrice(),
                p.getMrp(),
                p.getHsnSacCode(),
                p.getUnitId(),
                p.getTaxRateId());
    }

    private ProductLookupResponse fromVariant(RetailProductVariant v, String barcode) {
        Product parent = products.findByIdAndOrganizationId(v.getParentProductId(), org())
                .orElse(null);
        return new ProductLookupResponse(
                v.getParentProductId(),
                v.getId(),
                parent == null ? null : parent.getName(),
                barcode,
                v.getSellingPrice() != null ? v.getSellingPrice() : (parent == null ? null : parent.getSellingPrice()),
                v.getMrp() != null ? v.getMrp() : (parent == null ? null : parent.getMrp()),
                parent == null ? null : parent.getHsnSacCode(),
                parent == null ? null : parent.getUnitId(),
                parent == null ? null : parent.getTaxRateId());
    }

    private void logScan(
            String barcode,
            String source,
            String module,
            ResolvedMatch match,
            boolean success,
            String failureReason) {
        ScanHistory row = new ScanHistory();
        row.setOrganizationId(org());
        row.setBarcode(barcode);
        row.setSource(normalizeSource(source));
        row.setModule(module);
        if (match != null && match.product() != null) {
            row.setResolvedProductId(match.product().productId());
            row.setResolvedVariantId(match.product().variantId());
        }
        row.setSuccess(success);
        row.setFailureReason(failureReason);
        TenantContext.userId().ifPresent(row::setCreatedBy);
        scanHistory.save(row);
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "SCANNER";
        }
        return switch (source.toUpperCase(Locale.ROOT)) {
            case "MANUAL", "CAMERA" -> source.toUpperCase(Locale.ROOT);
            default -> "SCANNER";
        };
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private record ResolvedMatch(String matchType, ProductLookupResponse product) {}
}
