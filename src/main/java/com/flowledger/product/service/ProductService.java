package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.util.EntityCodeGenerator;
import com.flowledger.inventory.dto.InventoryDtos.Adjustment;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.product.dto.ProductDtos.*;
import com.flowledger.product.entity.Product;
import com.flowledger.product.entity.Unit;
import com.flowledger.product.mapper.ProductMapper;
import com.flowledger.product.repository.CategoryRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.product.repository.TaxRateRepository;
import com.flowledger.product.repository.UnitRepository;
import com.flowledger.search.event.SearchIndexEventPublisher;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.warehouse.entity.Warehouse;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProductService extends OrganizationScopedService {
    private final ProductRepository repo;
    private final ProductMapper mapper;
    private final CategoryRepository categories;
    private final UnitRepository units;
    private final TaxRateRepository taxRates;
    private final WarehouseRepository warehouses;
    private final OrganizationSettingsRepository orgSettings;
    private final InventoryService inventory;
    private final SearchIndexEventPublisher searchEvents;

    public ProductService(
            ProductRepository repo,
            ProductMapper mapper,
            CategoryRepository categories,
            UnitRepository units,
            TaxRateRepository taxRates,
            WarehouseRepository warehouses,
            OrganizationSettingsRepository orgSettings,
            InventoryService inventory,
            SearchIndexEventPublisher searchEvents) {
        this.repo = repo;
        this.mapper = mapper;
        this.categories = categories;
        this.units = units;
        this.taxRates = taxRates;
        this.warehouses = warehouses;
        this.orgSettings = orgSettings;
        this.inventory = inventory;
        this.searchEvents = searchEvents;
    }

    public Response create(Create dto) {
        UUID org = orgId();
        String sku = resolveSku(org, dto.sku(), dto.name());
        if (repo.existsByOrganizationIdAndSku(org, sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
        }
        Product product = mapper.toEntity(dto);
        product.setSku(sku);
        product.setOrganizationId(org);
        applyDefaults(product);
        if ("SERVICE".equalsIgnoreCase(product.getItemType())) {
            product.setOpeningStock(BigDecimal.ZERO);
            product.setMinimumStockLevel(BigDecimal.ZERO);
            product.setReorderLevel(BigDecimal.ZERO);
            product.setPurchasePrice(BigDecimal.ZERO);
            product.setMrp(BigDecimal.ZERO);
            product.setBatchTracking(false);
            product.setSerialTracking(false);
            product.setExpiryTracking(false);
            if (product.getHsnSacCode() != null && product.getHsnSacCode().isBlank()) {
                product.setHsnSacCode(null);
            }
            if (product.getUnitId() == null) {
                product.setUnitId(resolveDefaultUnitId(org));
            }
        } else if (product.getUnitId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unit is required for products");
        }
        Product saved = repo.save(product);
        postOpeningStockIfNeeded(saved, dto);
        searchEvents.upsert(org, SearchEntityType.PRODUCT, saved.getId());
        return toResponse(saved);
    }

    private void postOpeningStockIfNeeded(Product product, Create dto) {
        BigDecimal qty = product.getOpeningStock() == null ? BigDecimal.ZERO : product.getOpeningStock();
        if (qty.signum() <= 0) {
            return;
        }
        if (!"PRODUCT".equalsIgnoreCase(product.getItemType())) {
            return;
        }
        UUID warehouseId = resolveWarehouseId(dto.warehouseId());
        inventory.openingStock(new Adjustment(product.getId(), warehouseId, qty, "Product opening stock"));
    }

    private UUID resolveDefaultUnitId(UUID org) {
        return units.findBySystemUnitTrueOrOrganizationId(org).stream()
                .filter(Unit::isActive)
                .sorted((a, b) -> {
                    boolean aNos = "NOS".equalsIgnoreCase(a.getCode());
                    boolean bNos = "NOS".equalsIgnoreCase(b.getCode());
                    if (aNos != bNos) return aNos ? -1 : 1;
                    if (a.isSystemUnit() != b.isSystemUnit()) return a.isSystemUnit() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                })
                .map(Unit::getId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Create a unit before adding services"));
    }

    private UUID resolveWarehouseId(UUID requested) {
        UUID org = orgId();
        if (requested != null) {
            return warehouses
                    .findByIdAndOrganizationId(requested, org)
                    .map(Warehouse::getId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warehouse not found"));
        }
        UUID fromSettings = orgSettings
                .findByOrganizationId(org)
                .map(s -> s.getDefaultWarehouseId())
                .orElse(null);
        if (fromSettings != null) {
            var found = warehouses.findByIdAndOrganizationId(fromSettings, org);
            if (found.isPresent()) {
                return found.get().getId();
            }
        }
        return warehouses
                .findFirstByOrganizationIdAndDefaultWarehouseTrue(org)
                .or(() -> warehouses.findByOrganizationId(org).stream().findFirst())
                .map(Warehouse::getId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Create a warehouse before posting opening stock"));
    }

    private String resolveSku(UUID org, String provided, String name) {
        if (provided != null && !provided.isBlank()) {
            return provided.trim().toUpperCase(Locale.ROOT);
        }
        return EntityCodeGenerator.uniqueFromName(
                name, "SKU", candidate -> repo.existsByOrganizationIdAndSku(org, candidate));
    }

    private void applyDefaults(Product product) {
        if (product.getItemType() == null || product.getItemType().isBlank()) {
            product.setItemType("PRODUCT");
        }
        if (product.getPurchasePrice() == null) {
            product.setPurchasePrice(BigDecimal.ZERO);
        }
        if (product.getSellingPrice() == null) {
            product.setSellingPrice(BigDecimal.ZERO);
        }
        if (product.getMrp() == null) {
            product.setMrp(BigDecimal.ZERO);
        }
        if (product.getOpeningStock() == null) {
            product.setOpeningStock(BigDecimal.ZERO);
        }
        if (product.getMinimumStockLevel() == null) {
            product.setMinimumStockLevel(BigDecimal.ZERO);
        }
        if (product.getReorderLevel() == null) {
            product.setReorderLevel(BigDecimal.ZERO);
        }
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        return toResponse(load(id));
    }

    public Response update(UUID id, Update dto) {
        Product product = load(id);
        mapper.update(dto, product);
        if (dto.active() != null) {
            product.setActive(dto.active());
        }
        Product saved = repo.save(product);
        if (saved.isActive()) {
            searchEvents.upsert(orgId(), SearchEntityType.PRODUCT, saved.getId());
        } else {
            searchEvents.delete(orgId(), SearchEntityType.PRODUCT, saved.getId());
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<Response> search(Search filter, Pageable pageable) {
        UUID org = orgId();
        Specification<Product> spec = (root, query, builder) -> builder.equal(root.get("organizationId"), org);
        if (filter.active() != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("active"), filter.active()));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String q = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), q),
                    builder.like(builder.lower(root.get("sku")), q),
                    builder.like(builder.lower(root.get("barcode")), q)));
        }
        return repo.findAll(spec, pageable).map(this::toResponse);
    }

    private Product load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Product");
    }

    private Response toResponse(Product product) {
        UUID org = orgId();
        var base = mapper.toResponse(product);
        String categoryName = product.getCategoryId() == null
                ? null
                : categories
                        .findByIdAndOrganizationId(product.getCategoryId(), org)
                        .map(c -> c.getName())
                        .orElse(null);
        String unitName = product.getUnitId() == null
                ? null
                : units.findByIdAndOrganizationId(product.getUnitId(), org)
                        .map(u -> u.getName())
                        .orElse(null);
        String taxRateName = null;
        String taxType = null;
        if (product.getTaxRateId() != null) {
            var tax = taxRates.findByIdAndOrganizationId(product.getTaxRateId(), org);
            if (tax.isPresent()) {
                taxRateName = tax.get().getName();
                taxType = tax.get().getTaxType() == null ? "GST" : tax.get().getTaxType().name();
            }
        }
        return new Response(
                base.id(),
                base.itemType(),
                base.sku(),
                base.barcode(),
                base.name(),
                base.description(),
                base.categoryId(),
                categoryName,
                base.brand(),
                base.hsnSacCode(),
                base.unitId(),
                unitName,
                base.purchasePrice(),
                base.sellingPrice(),
                base.mrp(),
                base.taxRateId(),
                taxRateName,
                taxType,
                base.minimumStockLevel(),
                base.reorderLevel(),
                base.active());
    }
}
