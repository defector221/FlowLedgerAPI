package com.flowledger.sales.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.inventory.dto.InventoryDtos.PostTransaction;
import com.flowledger.inventory.entity.InventoryTransaction.Type;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.product.entity.Product;
import com.flowledger.product.entity.Unit;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.product.repository.UnitRepository;
import com.flowledger.sales.dto.SalesDtos.*;
import com.flowledger.sales.entity.*;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.search.event.SearchIndexEventPublisher;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.tax.TaxSplitDefaults;
import com.flowledger.tax.dto.GstCalculationDtos;
import com.flowledger.tax.service.GstCalculationService;
import com.flowledger.warehouse.entity.Warehouse;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesInvoiceService {
    private static final Pattern PAYMENT_TERMS_DAYS = Pattern.compile("(\\d{1,3})");

    private final SalesInvoiceRepository repo;
    private final InventoryService inventory;
    private final ProductRepository products;
    private final UnitRepository units;
    private final WarehouseRepository warehouses;
    private final OrganizationSettingsRepository orgSettings;
    private final OrganizationRepository orgs;
    private final CustomerRepository customers;
    private final DocumentNumberService numbers;
    private final GstCalculationService gst;
    private final SearchIndexEventPublisher searchEvents;

    public SalesInvoiceService(
            SalesInvoiceRepository r,
            InventoryService i,
            ProductRepository products,
            UnitRepository units,
            WarehouseRepository warehouses,
            OrganizationSettingsRepository orgSettings,
            OrganizationRepository o,
            CustomerRepository customers,
            DocumentNumberService n,
            GstCalculationService g,
            SearchIndexEventPublisher searchEvents) {
        repo = r;
        inventory = i;
        this.products = products;
        this.units = units;
        this.warehouses = warehouses;
        this.orgSettings = orgSettings;
        orgs = o;
        this.customers = customers;
        numbers = n;
        gst = g;
        this.searchEvents = searchEvents;
    }

    @Transactional
    public InvoiceDetail createDraft(Invoice d) {
        Organization o = orgs.findById(TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        SalesInvoice i = new SalesInvoice();
        i.setOrganizationId(o.getId());
        apply(i, d);
        ensureWarehouseForStockedItems(i);
        if (i.getInvoiceNumber() == null || i.getInvoiceNumber().isBlank()) {
            i.setInvoiceNumber(numbers.next(
                    o.getId(),
                    "SALES_INVOICE",
                    o.getInvoicePrefix(),
                    o.getInvoiceNumberFormat(),
                    o.getFinancialYearStart(),
                    i.getInvoiceDate()));
        }
        recalculate(i, o);
        SalesInvoice saved = repo.save(i);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return toDetail(saved);
    }

    @Transactional
    public InvoiceDetail updateDraft(UUID id, Invoice d) {
        SalesInvoice i = load(id);
        if (i.getStatus() != SalesInvoice.Status.DRAFT)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft invoices can be updated");
        Organization o = orgs.findById(i.getOrganizationId()).orElseThrow();
        apply(i, d);
        ensureWarehouseForStockedItems(i);
        recalculate(i, o);
        SalesInvoice saved = repo.save(i);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return toDetail(saved);
    }

    @Transactional
    public InvoiceDetail confirm(UUID id) {
        SalesInvoice i = load(id);
        if (i.getStatus() == SalesInvoice.Status.CANCELLED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled invoice cannot be confirmed");
        if (i.getStatus() == SalesInvoice.Status.CONFIRMED && i.isInventoryPosted()) return toDetail(i);
        Organization o = orgs.findById(i.getOrganizationId()).orElseThrow();
        if (i.getInvoiceNumber() == null || i.getInvoiceNumber().isBlank())
            i.setInvoiceNumber(numbers.next(
                    o.getId(),
                    "SALES_INVOICE",
                    o.getInvoicePrefix(),
                    o.getInvoiceNumberFormat(),
                    o.getFinancialYearStart(),
                    i.getInvoiceDate()));
        if (i.getDueDate() == null) {
            LocalDate invoiceDate = i.getInvoiceDate() != null ? i.getInvoiceDate() : LocalDate.now();
            i.setDueDate(resolveDueDate(i.getCustomerId(), invoiceDate));
        }
        recalculate(i, o);
        if (!i.isInventoryPosted()) {
            List<SalesInvoiceItem> stockableLines = stockableLines(i.getItems());
            if (!stockableLines.isEmpty()) {
                ensureWarehouseForStockedItems(i);
                if (i.getWarehouseId() == null)
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Warehouse is required when confirming stocked products");
                for (var line : stockableLines) {
                    BigDecimal available = inventory
                            .getStock(line.getProductId(), i.getWarehouseId())
                            .available();
                    if (!o.isAllowNegativeStock() && available.compareTo(line.getQuantity()) < 0) {
                        String productLabel = products
                                .findById(line.getProductId())
                                .map(Product::getName)
                                .filter(name -> name != null && !name.isBlank())
                                .orElse(line.getProductId().toString());
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Insufficient stock for \""
                                        + productLabel
                                        + "\". Available: "
                                        + available.stripTrailingZeros().toPlainString()
                                        + ", required: "
                                        + line.getQuantity().stripTrailingZeros().toPlainString());
                    }
                    inventory.postTransaction(new PostTransaction(
                            Type.SALE,
                            line.getProductId(),
                            i.getWarehouseId(),
                            BigDecimal.ZERO,
                            line.getQuantity(),
                            "SALES_INVOICE",
                            i.getId(),
                            i.getInvoiceNumber(),
                            "invoice:" + i.getId() + ":" + line.getId(),
                            null,
                            null,
                            null,
                            null,
                            i.getNotes(),
                            i.getInvoiceDate()));
                }
            }
            i.setInventoryPosted(true);
        }
        i.setStatus(SalesInvoice.Status.CONFIRMED);
        SalesInvoice saved = repo.save(i);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return toDetail(saved);
    }

    @Transactional
    public InvoiceDetail cancel(UUID id) {
        SalesInvoice i = load(id);
        if (i.getStatus() == SalesInvoice.Status.CANCELLED) return toDetail(i);
        if (i.isInventoryPosted()) {
            for (var line : stockableLines(i.getItems())) {
                if (i.getWarehouseId() == null) continue;
                inventory.postTransaction(new PostTransaction(
                        Type.SALES_RETURN,
                        line.getProductId(),
                        i.getWarehouseId(),
                        line.getQuantity(),
                        BigDecimal.ZERO,
                        "SALES_INVOICE_CANCEL",
                        i.getId(),
                        i.getInvoiceNumber(),
                        "invoice-cancel:" + i.getId() + ":" + line.getId(),
                        null,
                        null,
                        null,
                        null,
                        "Invoice cancellation",
                        LocalDate.now()));
            }
        }
        i.setInventoryPosted(false);
        i.setStatus(SalesInvoice.Status.CANCELLED);
        SalesInvoice saved = repo.save(i);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return toDetail(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceDetail get(UUID id) {
        return toDetail(load(id));
    }

    @Transactional(readOnly = true)
    public List<SalesInvoice> list(String status, UUID customerId) {
        return repo.findByOrganizationIdOrderByInvoiceDateDesc(TenantContext.getOrganizationId()).stream()
                .filter(i -> status == null || i.getStatus().name().equals(status))
                .filter(i -> customerId == null || customerId.equals(i.getCustomerId()))
                .toList();
    }

    private SalesInvoice load(UUID id) {
        return repo.findDetailedByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    private void apply(SalesInvoice i, Invoice d) {
        i.setCustomerId(d.customerId());
        LocalDate invoiceDate = d.invoiceDate() == null ? LocalDate.now() : d.invoiceDate();
        i.setInvoiceDate(invoiceDate);
        i.setDueDate(d.dueDate() != null ? d.dueDate() : resolveDueDate(d.customerId(), invoiceDate));
        i.setWarehouseId(d.warehouseId());
        i.setSalesOrderId(d.salesOrderId());
        i.setDeliveryChallanId(d.deliveryChallanId());
        i.setBillingAddress(d.billingAddress());
        i.setShippingAddress(d.shippingAddress());
        i.setPlaceOfSupply(d.placeOfSupply());
        i.setTaxInclusive(Boolean.TRUE.equals(d.taxInclusive()));
        i.setShippingCharges(z(d.shippingCharges()));
        i.setAdditionalCharges(z(d.additionalCharges()));
        i.setRoundOff(z(d.roundOff()));
        i.setNotes(d.notes());
        i.setTermsAndConditions(d.termsAndConditions());
        i.setTemplateId(d.templateId());
        i.getItems().clear();
        int n = 0;
        for (Item dline : d.items()) {
            SalesInvoiceItem l = new SalesInvoiceItem();
            l.setSalesInvoice(i);
            l.setProductId(dline.productId());
            l.setDescription(dline.description());
            l.setHsnSacCode(dline.hsnSacCode());
            l.setQuantity(dline.quantity());
            l.setUnitId(dline.unitId());
            l.setRate(dline.rate());
            l.setDiscountPercent(z(dline.discountPercent()));
            l.setTaxRate(z(dline.taxRate()));
            String taxType = TaxSplitDefaults.normalizeTaxType(dline.taxType());
            String strategy = TaxSplitDefaults.normalizeStrategy(dline.splitStrategy(), taxType);
            l.setTaxType(taxType);
            l.setSplitStrategy(strategy);
            l.setCgstSharePercent(TaxSplitDefaults.cgstShare(strategy, taxType, dline.cgstSharePercent()));
            l.setSgstSharePercent(TaxSplitDefaults.sgstShare(strategy, taxType, dline.sgstSharePercent()));
            l.setLineOrder(n++);
            i.getItems().add(l);
        }
    }

    private LocalDate resolveDueDate(UUID customerId, LocalDate invoiceDate) {
        int days = 30;
        if (customerId != null) {
            days = customers
                    .findByIdAndOrganizationId(customerId, TenantContext.getOrganizationId())
                    .map(c -> parsePaymentTermsDays(c.getPaymentTerms()))
                    .orElse(30);
        }
        return invoiceDate.plusDays(days);
    }

    private static int parsePaymentTermsDays(String paymentTerms) {
        if (paymentTerms == null || paymentTerms.isBlank()) return 30;
        Matcher matcher = PAYMENT_TERMS_DAYS.matcher(paymentTerms.trim());
        if (matcher.find()) {
            try {
                int days = Integer.parseInt(matcher.group(1));
                if (days >= 0 && days <= 365) return days;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 30;
    }

    private void ensureWarehouseForStockedItems(SalesInvoice i) {
        if (!stockableLines(i.getItems()).isEmpty() && i.getWarehouseId() == null) {
            i.setWarehouseId(resolveDefaultWarehouseId());
        }
    }

    private UUID resolveDefaultWarehouseId() {
        UUID org = TenantContext.getOrganizationId();
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
                        HttpStatus.BAD_REQUEST, "Create a warehouse before invoicing stocked products"));
    }

    private void recalculate(SalesInvoice i, Organization o) {
        BigDecimal sub = BigDecimal.ZERO,
                disc = BigDecimal.ZERO,
                taxable = BigDecimal.ZERO,
                cgst = BigDecimal.ZERO,
                sgst = BigDecimal.ZERO,
                igst = BigDecimal.ZERO;
        String orgState = o.getStateCode() == null || o.getStateCode().isBlank() ? "00" : o.getStateCode().trim();
        String pos = i.getPlaceOfSupply() == null || i.getPlaceOfSupply().isBlank() ? orgState : i.getPlaceOfSupply().trim();
        for (var l : i.getItems()) {
            BigDecimal discount = l.getQuantity()
                    .multiply(l.getRate())
                    .multiply(l.getDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            var r = gst.calculate(new GstCalculationDtos.Request(
                    orgState,
                    pos,
                    l.getTaxRate(),
                    i.isTaxInclusive(),
                    l.getQuantity(),
                    l.getRate(),
                    discount,
                    l.getTaxType(),
                    l.getSplitStrategy(),
                    l.getCgstSharePercent(),
                    l.getSgstSharePercent()));
            l.setDiscountAmount(discount);
            l.setTaxableAmount(r.taxable());
            l.setCgstAmount(r.cgst());
            l.setSgstAmount(r.sgst());
            l.setIgstAmount(r.igst().add(r.otherTax()));
            applyComponentRates(l, r);
            l.setLineTotal(r.lineTotal());
            sub = sub.add(l.getQuantity().multiply(l.getRate()));
            disc = disc.add(discount);
            taxable = taxable.add(r.taxable());
            cgst = cgst.add(r.cgst());
            sgst = sgst.add(r.sgst());
            igst = igst.add(r.igst()).add(r.otherTax());
        }
        i.setSubtotal(sub);
        i.setDiscountTotal(disc);
        i.setTaxableAmount(taxable);
        i.setCgstTotal(cgst);
        i.setSgstTotal(sgst);
        i.setIgstTotal(igst);
        i.setGrandTotal(i.getItems().stream()
                .map(SalesInvoiceItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(i.getShippingCharges())
                .add(i.getAdditionalCharges())
                .add(i.getRoundOff()));
        i.setOutstandingAmount(i.getGrandTotal().subtract(i.getAmountPaid()));
    }

    private InvoiceDetail toDetail(SalesInvoice i) {
        UUID org = i.getOrganizationId();
        final String warehouseName = i.getWarehouseId() == null
                ? null
                : warehouses
                        .findByIdAndOrganizationId(i.getWarehouseId(), org)
                        .map(Warehouse::getWarehouseName)
                        .orElse(null);
        Map<UUID, Product> productById = loadProducts(i.getItems(), org);
        Map<UUID, String> unitNameById = loadUnitNames(i.getItems(), productById, org);
        List<InvoiceItemDetail> items = i.getItems().stream()
                .map(line -> {
                    Product product = productById.get(line.getProductId());
                    boolean stocked = isStockedProduct(product);
                    String itemType = product == null
                            ? "PRODUCT"
                            : (product.getItemType() == null || product.getItemType().isBlank()
                                    ? "PRODUCT"
                                    : product.getItemType());
                    UUID unitId = line.getUnitId() != null
                            ? line.getUnitId()
                            : (product == null ? null : product.getUnitId());
                    return new InvoiceItemDetail(
                            line.getId(),
                            line.getProductId(),
                            product == null ? null : product.getName(),
                            itemType,
                            unitId == null ? null : unitNameById.get(unitId),
                            line.getDescription(),
                            line.getHsnSacCode(),
                            line.getQuantity(),
                            line.getRate(),
                            line.getDiscountPercent(),
                            line.getDiscountAmount(),
                            line.getTaxRate(),
                            line.getTaxType(),
                            line.getSplitStrategy(),
                            line.getCgstSharePercent(),
                            line.getSgstSharePercent(),
                            line.getLineTotal(),
                            stocked ? warehouseName : null);
                })
                .toList();
        return new InvoiceDetail(
                i.getId(),
                i.getInvoiceNumber(),
                i.getInvoiceDate(),
                i.getDueDate(),
                i.getCustomerId(),
                i.getWarehouseId(),
                warehouseName,
                i.getStatus().name(),
                i.getPaymentStatus(),
                i.getSubtotal(),
                i.getDiscountTotal(),
                i.getTaxableAmount(),
                i.getCgstTotal(),
                i.getSgstTotal(),
                i.getIgstTotal(),
                i.getShippingCharges(),
                i.getAdditionalCharges(),
                i.getRoundOff(),
                i.getGrandTotal(),
                i.getAmountPaid(),
                i.getOutstandingAmount(),
                i.getNotes(),
                i.getTermsAndConditions(),
                i.getTemplateId(),
                i.getBillingAddress(),
                i.getShippingAddress(),
                i.getPlaceOfSupply(),
                items);
    }

    private Map<UUID, Product> loadProducts(List<SalesInvoiceItem> lines, UUID org) {
        if (lines == null || lines.isEmpty()) return Map.of();
        Set<UUID> ids = lines.stream().map(SalesInvoiceItem::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> byId = new HashMap<>();
        for (UUID id : ids) {
            products.findByIdAndOrganizationId(id, org).ifPresent(p -> byId.put(p.getId(), p));
        }
        return byId;
    }

    private Map<UUID, String> loadUnitNames(
            List<SalesInvoiceItem> lines, Map<UUID, Product> productById, UUID org) {
        Set<UUID> unitIds = new HashSet<>();
        for (SalesInvoiceItem line : lines) {
            if (line.getUnitId() != null) unitIds.add(line.getUnitId());
            Product product = productById.get(line.getProductId());
            if (product != null && product.getUnitId() != null) unitIds.add(product.getUnitId());
        }
        Map<UUID, String> names = new HashMap<>();
        for (UUID unitId : unitIds) {
            units.findByIdAndOrganizationId(unitId, org)
                    .or(() -> units.findById(unitId).filter(Unit::isSystemUnit))
                    .ifPresent(u -> names.put(u.getId(), u.getName()));
        }
        return names;
    }

    private static void applyComponentRates(SalesInvoiceItem line, GstCalculationDtos.Response r) {
        BigDecimal rate = line.getTaxRate();
        BigDecimal hundred = BigDecimal.valueOf(100);
        if (r.cgst().signum() > 0 || r.sgst().signum() > 0) {
            BigDecimal cgstShare =
                    line.getCgstSharePercent() == null ? new BigDecimal("50") : line.getCgstSharePercent();
            BigDecimal sgstShare =
                    line.getSgstSharePercent() == null ? new BigDecimal("50") : line.getSgstSharePercent();
            line.setCgstRate(rate.multiply(cgstShare).divide(hundred, 4, RoundingMode.HALF_UP));
            line.setSgstRate(rate.multiply(sgstShare).divide(hundred, 4, RoundingMode.HALF_UP));
            line.setIgstRate(BigDecimal.ZERO);
        } else if (r.igst().signum() > 0 || r.otherTax().signum() > 0) {
            line.setCgstRate(BigDecimal.ZERO);
            line.setSgstRate(BigDecimal.ZERO);
            line.setIgstRate(rate);
        } else {
            line.setCgstRate(BigDecimal.ZERO);
            line.setSgstRate(BigDecimal.ZERO);
            line.setIgstRate(BigDecimal.ZERO);
        }
    }

    private List<SalesInvoiceItem> stockableLines(List<SalesInvoiceItem> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        UUID org = TenantContext.getOrganizationId();
        Map<UUID, Product> byId = loadProducts(lines, org);
        return lines.stream().filter(line -> isStockedProduct(byId.get(line.getProductId()))).toList();
    }

    private static boolean isStockedProduct(Product product) {
        if (product == null) return true;
        String type = product.getItemType();
        return type == null || type.isBlank() || "PRODUCT".equalsIgnoreCase(type);
    }

    private static BigDecimal z(BigDecimal x) {
        return x == null ? BigDecimal.ZERO : x;
    }
}
