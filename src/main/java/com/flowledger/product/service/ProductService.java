package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.util.EntityCodeGenerator;
import com.flowledger.product.dto.ProductDtos.*;
import com.flowledger.product.entity.Product;
import com.flowledger.product.mapper.ProductMapper;
import com.flowledger.product.repository.CategoryRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.product.repository.TaxRateRepository;
import com.flowledger.product.repository.UnitRepository;
import com.flowledger.search.event.SearchIndexEventPublisher;
import com.flowledger.search.model.SearchEntityType;
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
    private final SearchIndexEventPublisher searchEvents;

    public ProductService(
            ProductRepository repo,
            ProductMapper mapper,
            CategoryRepository categories,
            UnitRepository units,
            TaxRateRepository taxRates,
            SearchIndexEventPublisher searchEvents) {
        this.repo = repo;
        this.mapper = mapper;
        this.categories = categories;
        this.units = units;
        this.taxRates = taxRates;
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
        Product saved = repo.save(product);
        searchEvents.upsert(org, SearchEntityType.PRODUCT, saved.getId());
        return toResponse(saved);
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
            product.setPurchasePrice(java.math.BigDecimal.ZERO);
        }
        if (product.getSellingPrice() == null) {
            product.setSellingPrice(java.math.BigDecimal.ZERO);
        }
        if (product.getMrp() == null) {
            product.setMrp(java.math.BigDecimal.ZERO);
        }
        if (product.getOpeningStock() == null) {
            product.setOpeningStock(java.math.BigDecimal.ZERO);
        }
        if (product.getMinimumStockLevel() == null) {
            product.setMinimumStockLevel(java.math.BigDecimal.ZERO);
        }
        if (product.getReorderLevel() == null) {
            product.setReorderLevel(java.math.BigDecimal.ZERO);
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
            String pattern = "%" + filter.search().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("name")), pattern),
                    builder.like(builder.lower(root.get("sku")), pattern),
                    builder.like(builder.lower(root.get("barcode")), pattern)));
        }
        return repo.findAll(spec, pageable).map(this::toResponse);
    }

    private Response toResponse(Product product) {
        UUID org = orgId();
        Response base = mapper.toResponse(product);
        String categoryName = product.getCategoryId() == null
                ? null
                : categories
                        .findByIdAndOrganizationId(product.getCategoryId(), org)
                        .map(c -> c.getName())
                        .orElse(null);
        String unitName = product.getUnitId() == null
                ? null
                : units.findById(product.getUnitId()).map(u -> u.getName()).orElse(null);
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

    private Product load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Product");
    }
}
