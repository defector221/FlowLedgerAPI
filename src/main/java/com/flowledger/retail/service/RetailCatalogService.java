package com.flowledger.retail.service;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.retail.entity.RetailBrand;
import com.flowledger.retail.entity.RetailCollection;
import com.flowledger.retail.entity.RetailDepartment;
import com.flowledger.retail.entity.RetailProductBarcode;
import com.flowledger.retail.entity.RetailProductVariant;
import com.flowledger.retail.repository.RetailBrandRepository;
import com.flowledger.retail.repository.RetailCollectionRepository;
import com.flowledger.retail.repository.RetailDepartmentRepository;
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
public class RetailCatalogService {
    private final RetailModuleGuard guard;
    private final RetailBrandRepository brands;
    private final RetailDepartmentRepository departments;
    private final RetailCollectionRepository collections;
    private final RetailProductVariantRepository variants;
    private final RetailProductBarcodeRepository barcodes;
    private final ProductRepository products;

    public RetailCatalogService(
            RetailModuleGuard guard,
            RetailBrandRepository brands,
            RetailDepartmentRepository departments,
            RetailCollectionRepository collections,
            RetailProductVariantRepository variants,
            RetailProductBarcodeRepository barcodes,
            ProductRepository products) {
        this.guard = guard;
        this.brands = brands;
        this.departments = departments;
        this.collections = collections;
        this.variants = variants;
        this.barcodes = barcodes;
        this.products = products;
    }

    // ------------------------------------------------------------------ Brands
    @Transactional(readOnly = true)
    public List<BrandResponse> listBrands() {
        return brands.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .map(b -> new BrandResponse(b.getId(), b.getCode(), b.getName(), b.getVersion()))
                .toList();
    }

    public BrandResponse createBrand(BrandRequest r) {
        String code = code(r.code());
        if (brands.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Brand code already exists");
        }
        RetailBrand e = new RetailBrand();
        e.setOrganizationId(org());
        e.setCode(code);
        e.setName(r.name());
        audit(e, true);
        e = brands.save(e);
        return new BrandResponse(e.getId(), e.getCode(), e.getName(), e.getVersion());
    }

    public BrandResponse updateBrand(UUID id, BrandRequest r) {
        RetailBrand e = brands.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Brand not found"));
        e.setName(r.name());
        audit(e, false);
        e = brands.save(e);
        return new BrandResponse(e.getId(), e.getCode(), e.getName(), e.getVersion());
    }

    public void deleteBrand(UUID id) {
        RetailBrand e = brands.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Brand not found"));
        e.setDeleted(true);
        audit(e, false);
    }

    // ------------------------------------------------------------- Departments
    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments() {
        return departments.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .map(d -> new DepartmentResponse(d.getId(), d.getCode(), d.getName(), d.getVersion()))
                .toList();
    }

    public DepartmentResponse createDepartment(DepartmentRequest r) {
        String code = code(r.code());
        if (departments.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Department code already exists");
        }
        RetailDepartment e = new RetailDepartment();
        e.setOrganizationId(org());
        e.setCode(code);
        e.setName(r.name());
        audit(e, true);
        e = departments.save(e);
        return new DepartmentResponse(e.getId(), e.getCode(), e.getName(), e.getVersion());
    }

    public DepartmentResponse updateDepartment(UUID id, DepartmentRequest r) {
        RetailDepartment e = departments
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Department not found"));
        e.setName(r.name());
        audit(e, false);
        e = departments.save(e);
        return new DepartmentResponse(e.getId(), e.getCode(), e.getName(), e.getVersion());
    }

    public void deleteDepartment(UUID id) {
        RetailDepartment e = departments
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Department not found"));
        e.setDeleted(true);
        audit(e, false);
    }

    // ------------------------------------------------------------- Collections
    @Transactional(readOnly = true)
    public List<CollectionResponse> listCollections() {
        return collections.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org()).stream()
                .map(c -> new CollectionResponse(c.getId(), c.getCode(), c.getName(), c.getSeason(), c.getVersion()))
                .toList();
    }

    public CollectionResponse createCollection(CollectionRequest r) {
        String code = code(r.code());
        if (collections.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(org(), code)) {
            conflict("Collection code already exists");
        }
        RetailCollection e = new RetailCollection();
        e.setOrganizationId(org());
        e.setCode(code);
        e.setName(r.name());
        e.setSeason(r.season());
        audit(e, true);
        e = collections.save(e);
        return new CollectionResponse(e.getId(), e.getCode(), e.getName(), e.getSeason(), e.getVersion());
    }

    public CollectionResponse updateCollection(UUID id, CollectionRequest r) {
        RetailCollection e = collections
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Collection not found"));
        e.setName(r.name());
        e.setSeason(r.season());
        audit(e, false);
        e = collections.save(e);
        return new CollectionResponse(e.getId(), e.getCode(), e.getName(), e.getSeason(), e.getVersion());
    }

    public void deleteCollection(UUID id) {
        RetailCollection e = collections
                .findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Collection not found"));
        e.setDeleted(true);
        audit(e, false);
    }

    // ---------------------------------------------------------------- Variants
    @Transactional(readOnly = true)
    public List<VariantResponse> listVariants(UUID parentProductId) {
        return variants.findByOrganizationIdAndParentProductIdAndDeletedFalse(org(), parentProductId).stream()
                .map(this::map)
                .toList();
    }

    public VariantResponse createVariant(VariantRequest r) {
        RetailProductVariant e = new RetailProductVariant();
        e.setOrganizationId(org());
        applyVariant(e, r);
        audit(e, true);
        return map(variants.save(e));
    }

    public VariantResponse updateVariant(UUID id, VariantRequest r) {
        RetailProductVariant e = variants.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Variant not found"));
        applyVariant(e, r);
        audit(e, false);
        return map(variants.save(e));
    }

    public void deleteVariant(UUID id) {
        RetailProductVariant e = variants.findByIdAndOrganizationIdAndDeletedFalse(id, org())
                .orElseThrow(() -> notFound("Variant not found"));
        e.setDeleted(true);
        audit(e, false);
    }

    private void applyVariant(RetailProductVariant e, VariantRequest r) {
        e.setParentProductId(r.parentProductId());
        e.setSku(r.sku());
        e.setBarcode(r.barcode());
        e.setColor(r.color());
        e.setSize(r.size());
        e.setWeight(r.weight());
        e.setCapacity(r.capacity());
        e.setPattern(r.pattern());
        e.setMaterial(r.material());
        e.setSellingPrice(r.sellingPrice());
        e.setMrp(r.mrp());
        e.setActive(r.active() == null || r.active());
    }

    private VariantResponse map(RetailProductVariant e) {
        return new VariantResponse(
                e.getId(),
                e.getParentProductId(),
                e.getSku(),
                e.getBarcode(),
                e.getColor(),
                e.getSize(),
                e.getWeight(),
                e.getCapacity(),
                e.getPattern(),
                e.getMaterial(),
                e.getSellingPrice(),
                e.getMrp(),
                e.isActive(),
                e.getVersion());
    }

    // ---------------------------------------------------------------- Barcodes
    @Transactional(readOnly = true)
    public List<BarcodeResponse> listBarcodes(UUID productId) {
        return barcodes.findByOrganizationIdAndProductId(org(), productId).stream()
                .map(this::map)
                .toList();
    }

    public BarcodeResponse createBarcode(BarcodeRequest r) {
        barcodes.findByOrganizationIdAndBarcode(org(), r.barcode()).ifPresent(b -> conflict("Barcode already exists"));
        RetailProductBarcode e = new RetailProductBarcode();
        e.setOrganizationId(org());
        e.setProductId(r.productId());
        e.setVariantId(r.variantId());
        e.setBarcode(r.barcode());
        e.setBarcodeType(r.barcodeType() == null ? "EAN13" : r.barcodeType());
        e.setPrimary(r.primary() != null && r.primary());
        audit(e, true);
        return map(barcodes.save(e));
    }

    public void deleteBarcode(UUID id) {
        RetailProductBarcode e = barcodes.findById(id)
                .filter(b -> b.getOrganizationId().equals(org()))
                .orElseThrow(() -> notFound("Barcode not found"));
        barcodes.delete(e);
    }

    private BarcodeResponse map(RetailProductBarcode e) {
        return new BarcodeResponse(
                e.getId(), e.getProductId(), e.getVariantId(), e.getBarcode(), e.getBarcodeType(), e.isPrimary());
    }

    // ------------------------------------------------------------ Barcode lookup
    @Transactional(readOnly = true)
    public ProductLookupResponse lookupByBarcode(String barcode) {
        UUID org = org();

        // 1. Explicit retail barcode mapping.
        Optional<RetailProductBarcode> mapping = barcodes.findByOrganizationIdAndBarcode(org, barcode);
        if (mapping.isPresent()) {
            RetailProductBarcode m = mapping.get();
            if (m.getVariantId() != null) {
                Optional<RetailProductVariant> v =
                        variants.findByIdAndOrganizationIdAndDeletedFalse(m.getVariantId(), org);
                if (v.isPresent()) {
                    return fromVariant(v.get(), barcode);
                }
            }
            if (m.getProductId() != null) {
                return products.findByIdAndOrganizationId(m.getProductId(), org)
                        .map(p -> fromProduct(p, barcode))
                        .orElseThrow(() -> notFound("Product not found for barcode"));
            }
        }

        // 2. Variant-level barcode.
        Optional<RetailProductVariant> variant =
                variants.findFirstByOrganizationIdAndBarcodeAndDeletedFalse(org, barcode);
        if (variant.isPresent()) {
            return fromVariant(variant.get(), barcode);
        }

        // 3. Core product barcode.
        Optional<Product> byBarcode = products.findFirstByOrganizationIdAndBarcode(org, barcode);
        if (byBarcode.isPresent()) {
            return fromProduct(byBarcode.get(), barcode);
        }

        // 4. Name / SKU fallback for typed POS search.
        String needle = barcode.trim().toLowerCase(Locale.ROOT);
        List<Product> active = products.findByOrganizationIdAndActiveTrue(org);
        Optional<Product> exact = active.stream()
                .filter(p -> (p.getName() != null && p.getName().equalsIgnoreCase(barcode.trim()))
                        || (p.getSku() != null && p.getSku().equalsIgnoreCase(barcode.trim())))
                .findFirst();
        if (exact.isPresent()) {
            Product p = exact.get();
            return fromProduct(p, p.getBarcode() != null ? p.getBarcode() : barcode);
        }
        Optional<Product> partial = active.stream()
                .filter(p -> (p.getName() != null
                                && p.getName().toLowerCase(Locale.ROOT).contains(needle))
                        || (p.getSku() != null
                                && p.getSku().toLowerCase(Locale.ROOT).contains(needle)))
                .findFirst();
        return partial.map(p -> fromProduct(p, p.getBarcode() != null ? p.getBarcode() : barcode))
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

    // ----------------------------------------------------------------- Helpers
    private UUID org() {
        return guard.ensureEnabled();
    }

    private String code(String provided) {
        return provided.trim().toUpperCase(Locale.ROOT);
    }

    private void audit(com.flowledger.common.entity.AuditedEntity e, boolean created) {
        TenantContext.userId().ifPresent(u -> {
            if (created) {
                e.setCreatedBy(u);
            }
            e.setUpdatedBy(u);
        });
    }

    private ResponseStatusException notFound(String m) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
    }

    private void conflict(String m) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, m);
    }
}
