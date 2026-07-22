package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.EntityCodeGenerator;
import com.flowledger.product.dto.SupplierCatalogDtos.Create;
import com.flowledger.product.dto.SupplierCatalogDtos.Response;
import com.flowledger.product.dto.SupplierCatalogDtos.Update;
import com.flowledger.product.entity.Product;
import com.flowledger.product.entity.SupplierCatalogItem;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.product.repository.SupplierCatalogItemRepository;
import com.flowledger.supplier.entity.Supplier;
import com.flowledger.supplier.repository.SupplierRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class SupplierCatalogService extends OrganizationScopedService {
    private final SupplierCatalogItemRepository repository;
    private final ProductRepository products;
    private final SupplierRepository suppliers;

    public SupplierCatalogService(
            SupplierCatalogItemRepository repository,
            ProductRepository products,
            SupplierRepository suppliers) {
        this.repository = repository;
        this.products = products;
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public List<Response> listBySupplier(UUID supplierId) {
        requireSupplier(supplierId);
        return repository
                .findByOrganizationIdAndSupplierIdAndActiveTrueAndDeletedFalseOrderBySupplierProductNameAsc(
                        orgId(), supplierId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Response> listActiveBySupplier(UUID supplierId) {
        requireSupplier(supplierId);
        return repository.findActiveForSupplier(orgId(), supplierId, LocalDate.now()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Response> listByProduct(UUID productId) {
        requireProduct(productId);
        return repository.findByOrganizationIdAndProductIdAndDeletedFalseOrderByPreferredDesc(orgId(), productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Response get(UUID supplierId, UUID id) {
        return toResponse(load(supplierId, id));
    }

    public Response create(UUID supplierId, Create dto) {
        UUID productId = dto.productId();
        if (productId == null) {
            throw badRequest("productId is required");
        }
        return createItem(supplierId, productId, dto);
    }

    public Response createForProduct(UUID productId, Create dto) {
        UUID supplierId = dto.supplierId();
        if (supplierId == null) {
            throw badRequest("supplierId is required");
        }
        return createItem(supplierId, productId, dto);
    }

    public Response update(UUID supplierId, UUID id, Update dto) {
        SupplierCatalogItem item = load(supplierId, id);
        validateDates(dto.validFrom(), dto.validTo());
        boolean willBeActive = dto.active() == null ? item.isActive() : dto.active();
        boolean willBePreferred = dto.preferred() == null ? item.isPreferred() : dto.preferred();
        if (willBePreferred && willBeActive) {
            clearOtherPreferred(item.getProductId(), item.getId());
        }
        if (dto.supplierSku() != null) {
            if (dto.supplierSku().isBlank()) {
                Product product = products.findByIdAndOrganizationId(item.getProductId(), orgId()).orElseThrow();
                Supplier supplier = suppliers.findByIdAndOrganizationId(item.getSupplierId(), orgId()).orElseThrow();
                item.setSupplierSku(resolveSupplierSku(orgId(), null, product.getSku(), supplier.getSupplierCode()));
            } else {
                item.setSupplierSku(dto.supplierSku().trim().toUpperCase(Locale.ROOT));
            }
        }
        if (dto.supplierProductName() != null) item.setSupplierProductName(dto.supplierProductName());
        if (dto.purchasePrice() != null) item.setPurchasePrice(dto.purchasePrice());
        if (dto.currency() != null) item.setCurrency(normalizeCurrency(dto.currency()));
        if (dto.moq() != null) item.setMoq(dto.moq());
        if (dto.leadTimeDays() != null) item.setLeadTimeDays(dto.leadTimeDays());
        if (dto.validFrom() != null) item.setValidFrom(dto.validFrom());
        if (dto.validTo() != null) item.setValidTo(dto.validTo());
        if (dto.notes() != null) item.setNotes(dto.notes());
        if (dto.active() != null) item.setActive(dto.active());
        if (dto.preferred() != null) item.setPreferred(dto.preferred());
        validateDates(item.getValidFrom(), item.getValidTo());
        TenantContext.userId().ifPresent(item::setUpdatedBy);
        return toResponse(repository.save(item));
    }

    public void softDelete(UUID supplierId, UUID id) {
        SupplierCatalogItem item = load(supplierId, id);
        item.setDeleted(true);
        item.setActive(false);
        item.setPreferred(false);
        TenantContext.userId().ifPresent(item::setUpdatedBy);
        repository.save(item);
    }

    @Transactional(readOnly = true)
    public SupplierCatalogItem requireActiveCatalogItem(UUID organizationId, UUID supplierId, UUID productId) {
        if (!orgId().equals(organizationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier catalog item not found");
        }
        SupplierCatalogItem item = repository
                .findByOrganizationIdAndProductIdAndSupplierIdAndActiveTrueAndDeletedFalse(
                        organizationId, productId, supplierId)
                .orElseThrow(() -> badRequest("Product is not in the selected supplier's active catalog"));
        LocalDate today = LocalDate.now();
        if ((item.getValidFrom() != null && item.getValidFrom().isAfter(today))
                || (item.getValidTo() != null && item.getValidTo().isBefore(today))) {
            throw badRequest("Product is not currently valid in the selected supplier's catalog");
        }
        return item;
    }

    private Response createItem(UUID supplierId, UUID productId, Create dto) {
        UUID org = orgId();
        requireSupplier(supplierId);
        requireProduct(productId);
        validateDates(dto.validFrom(), dto.validTo());
        if (repository
                .findByOrganizationIdAndProductIdAndSupplierIdAndDeletedFalse(org, productId, supplierId)
                .isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier catalog item already exists");
        }

        SupplierCatalogItem item = new SupplierCatalogItem();
        item.setOrganizationId(org);
        item.setProductId(productId);
        item.setSupplierId(supplierId);
        Product product = products.findByIdAndOrganizationId(productId, org).orElseThrow();
        Supplier supplier = suppliers.findByIdAndOrganizationId(supplierId, org).orElseThrow();
        item.setSupplierSku(resolveSupplierSku(org, dto.supplierSku(), product.getSku(), supplier.getSupplierCode()));
        item.setSupplierProductName(
                dto.supplierProductName() == null || dto.supplierProductName().isBlank()
                        ? product.getName()
                        : dto.supplierProductName());
        item.setPurchasePrice(dto.purchasePrice());
        item.setCurrency(normalizeCurrency(dto.currency()));
        item.setMoq(dto.moq());
        item.setLeadTimeDays(dto.leadTimeDays());
        item.setPreferred(Boolean.TRUE.equals(dto.preferred()));
        item.setValidFrom(dto.validFrom());
        item.setValidTo(dto.validTo());
        item.setNotes(dto.notes());
        item.setActive(dto.active() == null || dto.active());
        TenantContext.userId().ifPresent(user -> {
            item.setCreatedBy(user);
            item.setUpdatedBy(user);
        });
        if (item.isPreferred() && item.isActive()) {
            clearOtherPreferred(productId, null);
        }
        return toResponse(repository.save(item));
    }

    private String resolveSupplierSku(UUID org, String provided, String productSku, String supplierCode) {
        if (provided != null && !provided.isBlank()) {
            return provided.trim().toUpperCase(Locale.ROOT);
        }
        String seed = (productSku == null || productSku.isBlank() ? "SKU" : productSku)
                + "-"
                + (supplierCode == null || supplierCode.isBlank() ? "SUP" : supplierCode);
        return EntityCodeGenerator.uniqueFromName(
                seed,
                "SSKU",
                candidate -> repository.existsByOrganizationIdAndSupplierSkuIgnoreCaseAndDeletedFalse(org, candidate));
    }

    private void clearOtherPreferred(UUID productId, UUID currentId) {
        UUID excludedId = currentId == null ? new UUID(0, 0) : currentId;
        List<SupplierCatalogItem> others = repository
                .findByOrganizationIdAndProductIdAndPreferredTrueAndActiveTrueAndDeletedFalseAndIdNot(
                        orgId(), productId, excludedId);
        others.forEach(item -> {
            item.setPreferred(false);
            TenantContext.userId().ifPresent(item::setUpdatedBy);
        });
        repository.saveAllAndFlush(others);
    }

    private SupplierCatalogItem load(UUID supplierId, UUID id) {
        return required(
                repository.findByIdAndOrganizationIdAndSupplierIdAndDeletedFalse(id, orgId(), supplierId),
                "Supplier catalog item");
    }

    private void requireSupplier(UUID supplierId) {
        required(suppliers.findByIdAndOrganizationId(supplierId, orgId()), "Supplier");
    }

    private void requireProduct(UUID productId) {
        required(products.findByIdAndOrganizationId(productId, orgId()), "Product");
    }

    private void validateDates(LocalDate validFrom, LocalDate validTo) {
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw badRequest("validTo must be on or after validFrom");
        }
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "INR" : currency.toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Response toResponse(SupplierCatalogItem item) {
        var product = products.findByIdAndOrganizationId(item.getProductId(), orgId()).orElse(null);
        var supplier = suppliers.findByIdAndOrganizationId(item.getSupplierId(), orgId()).orElse(null);
        return new Response(
                item.getId(),
                item.getProductId(),
                product == null ? null : product.getName(),
                product == null ? null : product.getSku(),
                product == null ? null : product.getItemType(),
                product == null ? null : product.getTaxRateId(),
                item.getSupplierId(),
                supplier == null ? null : supplier.getSupplierName(),
                item.getSupplierSku(),
                item.getSupplierProductName(),
                item.getPurchasePrice(),
                item.getCurrency(),
                item.getMoq(),
                item.getLeadTimeDays(),
                item.isPreferred(),
                item.getValidFrom(),
                item.getValidTo(),
                item.getNotes(),
                item.isActive(),
                item.getVersion(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
