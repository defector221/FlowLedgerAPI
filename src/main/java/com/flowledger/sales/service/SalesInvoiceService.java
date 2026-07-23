package com.flowledger.sales.service;

import com.flowledger.accounting.domain.AccountingStatus;
import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.service.AccountingPostingService;
import com.flowledger.ai.workflow.AiWorkflowGateService;
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
import com.flowledger.subscription.service.SubscriptionService;
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
import org.springframework.beans.factory.ObjectProvider;
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
    private final AccountingPostingService accounting;
    private final SubscriptionService subscriptions;
    private final ObjectProvider<AiWorkflowGateService> workflowGate;

    public SalesInvoiceService(
            SalesInvoiceRepository salesInvoiceRepository,
            InventoryService inventoryService,
            ProductRepository products,
            UnitRepository units,
            WarehouseRepository warehouses,
            OrganizationSettingsRepository orgSettings,
            OrganizationRepository organizationRepository,
            CustomerRepository customers,
            DocumentNumberService documentNumberService,
            GstCalculationService gstCalculationService,
            SearchIndexEventPublisher searchEvents,
            AccountingPostingService accounting,
            SubscriptionService subscriptions,
            ObjectProvider<AiWorkflowGateService> workflowGate) {
        repo = salesInvoiceRepository;
        inventory = inventoryService;
        this.products = products;
        this.units = units;
        this.warehouses = warehouses;
        this.orgSettings = orgSettings;
        orgs = organizationRepository;
        this.customers = customers;
        numbers = documentNumberService;
        gst = gstCalculationService;
        this.searchEvents = searchEvents;
        this.accounting = accounting;
        this.subscriptions = subscriptions;
        this.workflowGate = workflowGate;
    }

    @Transactional
    public InvoiceDetail createDraft(Invoice d) {
        Organization organization = orgs.findById(TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        subscriptions.checkCanCreateInvoice(organization.getId());
        SalesInvoice invoice = new SalesInvoice();
        invoice.setOrganizationId(organization.getId());
        apply(invoice, d);
        ensureWarehouseForStockedItems(invoice);
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            invoice.setInvoiceNumber(numbers.next(
                    organization.getId(),
                    "SALES_INVOICE",
                    organization.getInvoicePrefix(),
                    organization.getInvoiceNumberFormat(),
                    organization.getFinancialYearStart(),
                    invoice.getInvoiceDate()));
        }
        recalculate(invoice, organization);
        SalesInvoice saved = repo.save(invoice);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return toDetail(saved);
    }

    @Transactional
    public InvoiceDetail updateDraft(UUID id, Invoice d) {
        SalesInvoice invoice = load(id);
        if (invoice.getStatus() != SalesInvoice.Status.DRAFT)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only draft invoices can be updated");
        Organization organization = orgs.findById(invoice.getOrganizationId()).orElseThrow();
        apply(invoice, d);
        ensureWarehouseForStockedItems(invoice);
        recalculate(invoice, organization);
        SalesInvoice saved = repo.save(invoice);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return toDetail(saved);
    }

    @Transactional
    public InvoiceDetail confirm(UUID id) {
        SalesInvoice invoice = load(id);
        if (invoice.getStatus() == SalesInvoice.Status.CANCELLED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled invoice cannot be confirmed");
        if (invoice.getStatus() == SalesInvoice.Status.CONFIRMED && invoice.isInventoryPosted())
            return toDetail(invoice);
        AiWorkflowGateService gate = workflowGate.getIfAvailable();
        if (gate != null) {
            gate.requireApproved("SALES_INVOICE", invoice.getId(), invoice.getGrandTotal(), "confirm invoice");
        }
        Organization organization = orgs.findById(invoice.getOrganizationId()).orElseThrow();
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank())
            invoice.setInvoiceNumber(numbers.next(
                    organization.getId(),
                    "SALES_INVOICE",
                    organization.getInvoicePrefix(),
                    organization.getInvoiceNumberFormat(),
                    organization.getFinancialYearStart(),
                    invoice.getInvoiceDate()));
        if (invoice.getDueDate() == null) {
            LocalDate invoiceDate = invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now();
            invoice.setDueDate(resolveDueDate(invoice.getCustomerId(), invoiceDate));
        }
        recalculate(invoice, organization);
        if (!invoice.isInventoryPosted()) {
            List<SalesInvoiceItem> stockableLines = stockableLines(invoice.getItems());
            if (!stockableLines.isEmpty()) {
                ensureWarehouseForStockedItems(invoice);
                if (invoice.getWarehouseId() == null)
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Warehouse is required when confirming stocked products");
                for (var line : stockableLines) {
                    BigDecimal available = inventory
                            .getStock(line.getProductId(), invoice.getWarehouseId())
                            .available();
                    if (!organization.isAllowNegativeStock() && available.compareTo(line.getQuantity()) < 0) {
                        String productLabel = products.findById(line.getProductId())
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
                                        + line.getQuantity()
                                                .stripTrailingZeros()
                                                .toPlainString());
                    }
                    inventory.postTransaction(new PostTransaction(
                            Type.SALE,
                            line.getProductId(),
                            invoice.getWarehouseId(),
                            BigDecimal.ZERO,
                            line.getQuantity(),
                            "SALES_INVOICE",
                            invoice.getId(),
                            invoice.getInvoiceNumber(),
                            "invoice:" + invoice.getId() + ":" + line.getId(),
                            null,
                            null,
                            null,
                            null,
                            invoice.getNotes(),
                            invoice.getInvoiceDate()));
                }
            }
            invoice.setInventoryPosted(true);
        }
        invoice.setStatus(SalesInvoice.Status.CONFIRMED);
        SalesInvoice saved = repo.save(invoice);
        accounting.postSalesInvoice(saved);
        searchEvents.upsert(saved.getOrganizationId(), SearchEntityType.SALES_INVOICE, saved.getId());
        return toDetail(saved);
    }

    @Transactional
    public InvoiceDetail cancel(UUID id) {
        SalesInvoice invoice = load(id);
        if (invoice.getStatus() == SalesInvoice.Status.CANCELLED) return toDetail(invoice);
        if (invoice.isInventoryPosted()) {
            for (var line : stockableLines(invoice.getItems())) {
                if (invoice.getWarehouseId() == null) continue;
                inventory.postTransaction(new PostTransaction(
                        Type.SALES_RETURN,
                        line.getProductId(),
                        invoice.getWarehouseId(),
                        line.getQuantity(),
                        BigDecimal.ZERO,
                        "SALES_INVOICE_CANCEL",
                        invoice.getId(),
                        invoice.getInvoiceNumber(),
                        "invoice-cancel:" + invoice.getId() + ":" + line.getId(),
                        null,
                        null,
                        null,
                        null,
                        "Invoice cancellation",
                        LocalDate.now()));
            }
        }
        invoice.setInventoryPosted(false);
        invoice.setStatus(SalesInvoice.Status.CANCELLED);
        if (invoice.getAccountingStatus() == AccountingStatus.POSTED) {
            accounting.reverseDocumentJournal(
                    invoice.getOrganizationId(), JournalSource.SALES_INVOICE, invoice.getId());
            invoice.setAccountingStatus(AccountingStatus.REVERSED);
        }
        SalesInvoice saved = repo.save(invoice);
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
                .filter(invoice -> status == null || invoice.getStatus().name().equals(status))
                .filter(invoice -> customerId == null || customerId.equals(invoice.getCustomerId()))
                .toList();
    }

    private SalesInvoice load(UUID id) {
        return repo.findDetailedByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
    }

    private void apply(SalesInvoice invoice, Invoice d) {
        invoice.setCustomerId(d.customerId());
        LocalDate invoiceDate = d.invoiceDate() == null ? LocalDate.now() : d.invoiceDate();
        invoice.setInvoiceDate(invoiceDate);
        invoice.setDueDate(d.dueDate() != null ? d.dueDate() : resolveDueDate(d.customerId(), invoiceDate));
        invoice.setWarehouseId(d.warehouseId());
        invoice.setSalesOrderId(d.salesOrderId());
        invoice.setDeliveryChallanId(d.deliveryChallanId());
        invoice.setBillingAddress(d.billingAddress());
        invoice.setShippingAddress(d.shippingAddress());
        invoice.setPlaceOfSupply(d.placeOfSupply());
        invoice.setTaxInclusive(Boolean.TRUE.equals(d.taxInclusive()));
        invoice.setShippingCharges(z(d.shippingCharges()));
        invoice.setAdditionalCharges(z(d.additionalCharges()));
        invoice.setRoundOff(z(d.roundOff()));
        invoice.setNotes(d.notes());
        invoice.setTermsAndConditions(d.termsAndConditions());
        invoice.setTemplateId(d.templateId());
        invoice.getItems().clear();
        int n = 0;
        for (Item dline : d.items()) {
            SalesInvoiceItem line = new SalesInvoiceItem();
            line.setSalesInvoice(invoice);
            line.setProductId(dline.productId());
            line.setDescription(dline.description());
            line.setHsnSacCode(dline.hsnSacCode());
            line.setQuantity(dline.quantity());
            line.setUnitId(dline.unitId());
            line.setRate(dline.rate());
            line.setDiscountPercent(z(dline.discountPercent()));
            line.setTaxRate(z(dline.taxRate()));
            String taxType = TaxSplitDefaults.normalizeTaxType(dline.taxType());
            String strategy = TaxSplitDefaults.normalizeStrategy(dline.splitStrategy(), taxType);
            line.setTaxType(taxType);
            line.setSplitStrategy(strategy);
            line.setCgstSharePercent(TaxSplitDefaults.cgstShare(strategy, taxType, dline.cgstSharePercent()));
            line.setSgstSharePercent(TaxSplitDefaults.sgstShare(strategy, taxType, dline.sgstSharePercent()));
            line.setLineOrder(n++);
            invoice.getItems().add(line);
        }
    }

    private LocalDate resolveDueDate(UUID customerId, LocalDate invoiceDate) {
        int days = 30;
        if (customerId != null) {
            days = customers
                    .findByIdAndOrganizationId(customerId, TenantContext.getOrganizationId())
                    .map(customer -> parsePaymentTermsDays(customer.getPaymentTerms()))
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

    private void ensureWarehouseForStockedItems(SalesInvoice invoice) {
        if (!stockableLines(invoice.getItems()).isEmpty() && invoice.getWarehouseId() == null) {
            invoice.setWarehouseId(resolveDefaultWarehouseId());
        }
    }

    private UUID resolveDefaultWarehouseId() {
        UUID org = TenantContext.getOrganizationId();
        UUID fromSettings = orgSettings
                .findByOrganizationId(org)
                .map(settings -> settings.getDefaultWarehouseId())
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

    private void recalculate(SalesInvoice invoice, Organization organization) {
        BigDecimal sub = BigDecimal.ZERO,
                disc = BigDecimal.ZERO,
                taxable = BigDecimal.ZERO,
                cgst = BigDecimal.ZERO,
                sgst = BigDecimal.ZERO,
                igst = BigDecimal.ZERO;
        String orgState = organization.getStateCode() == null
                        || organization.getStateCode().isBlank()
                ? "00"
                : organization.getStateCode().trim();
        String pos =
                invoice.getPlaceOfSupply() == null || invoice.getPlaceOfSupply().isBlank()
                        ? orgState
                        : invoice.getPlaceOfSupply().trim();
        for (var line : invoice.getItems()) {
            BigDecimal discount = line.getQuantity()
                    .multiply(line.getRate())
                    .multiply(line.getDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            var gstResult = gst.calculate(new GstCalculationDtos.Request(
                    orgState,
                    pos,
                    line.getTaxRate(),
                    invoice.isTaxInclusive(),
                    line.getQuantity(),
                    line.getRate(),
                    discount,
                    line.getTaxType(),
                    line.getSplitStrategy(),
                    line.getCgstSharePercent(),
                    line.getSgstSharePercent()));
            line.setDiscountAmount(discount);
            line.setTaxableAmount(gstResult.taxable());
            line.setCgstAmount(gstResult.cgst());
            line.setSgstAmount(gstResult.sgst());
            line.setIgstAmount(gstResult.igst().add(gstResult.otherTax()));
            applyComponentRates(line, gstResult);
            line.setLineTotal(gstResult.lineTotal());
            sub = sub.add(line.getQuantity().multiply(line.getRate()));
            disc = disc.add(discount);
            taxable = taxable.add(gstResult.taxable());
            cgst = cgst.add(gstResult.cgst());
            sgst = sgst.add(gstResult.sgst());
            igst = igst.add(gstResult.igst()).add(gstResult.otherTax());
        }
        invoice.setSubtotal(sub);
        invoice.setDiscountTotal(disc);
        invoice.setTaxableAmount(taxable);
        invoice.setCgstTotal(cgst);
        invoice.setSgstTotal(sgst);
        invoice.setIgstTotal(igst);
        invoice.setGrandTotal(invoice.getItems().stream()
                .map(SalesInvoiceItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(invoice.getShippingCharges())
                .add(invoice.getAdditionalCharges())
                .add(invoice.getRoundOff()));
        invoice.setOutstandingAmount(invoice.getGrandTotal().subtract(invoice.getAmountPaid()));
    }

    private InvoiceDetail toDetail(SalesInvoice invoice) {
        UUID org = invoice.getOrganizationId();
        final String warehouseName = invoice.getWarehouseId() == null
                ? null
                : warehouses
                        .findByIdAndOrganizationId(invoice.getWarehouseId(), org)
                        .map(Warehouse::getWarehouseName)
                        .orElse(null);
        Map<UUID, Product> productById = loadProducts(invoice.getItems(), org);
        Map<UUID, String> unitNameById = loadUnitNames(invoice.getItems(), productById, org);
        List<InvoiceItemDetail> items = invoice.getItems().stream()
                .map(line -> {
                    Product product = productById.get(line.getProductId());
                    boolean stocked = isStockedProduct(product);
                    String itemType = product == null
                            ? "PRODUCT"
                            : (product.getItemType() == null
                                            || product.getItemType().isBlank()
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
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getDueDate(),
                invoice.getCustomerId(),
                invoice.getWarehouseId(),
                warehouseName,
                invoice.getStatus().name(),
                invoice.getPaymentStatus(),
                invoice.getSubtotal(),
                invoice.getDiscountTotal(),
                invoice.getTaxableAmount(),
                invoice.getCgstTotal(),
                invoice.getSgstTotal(),
                invoice.getIgstTotal(),
                invoice.getShippingCharges(),
                invoice.getAdditionalCharges(),
                invoice.getRoundOff(),
                invoice.getGrandTotal(),
                invoice.getAmountPaid(),
                invoice.getOutstandingAmount(),
                invoice.getNotes(),
                invoice.getTermsAndConditions(),
                invoice.getTemplateId(),
                invoice.getBillingAddress(),
                invoice.getShippingAddress(),
                invoice.getPlaceOfSupply(),
                items);
    }

    private Map<UUID, Product> loadProducts(List<SalesInvoiceItem> lines, UUID org) {
        if (lines == null || lines.isEmpty()) return Map.of();
        Set<UUID> ids = lines.stream().map(SalesInvoiceItem::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> byId = new HashMap<>();
        for (UUID id : ids) {
            products.findByIdAndOrganizationId(id, org).ifPresent(product -> byId.put(product.getId(), product));
        }
        return byId;
    }

    private Map<UUID, String> loadUnitNames(List<SalesInvoiceItem> lines, Map<UUID, Product> productById, UUID org) {
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
                    .ifPresent(unit -> names.put(unit.getId(), unit.getName()));
        }
        return names;
    }

    private static void applyComponentRates(SalesInvoiceItem line, GstCalculationDtos.Response gstResult) {
        BigDecimal rate = line.getTaxRate();
        BigDecimal hundred = BigDecimal.valueOf(100);
        if (gstResult.cgst().signum() > 0 || gstResult.sgst().signum() > 0) {
            BigDecimal cgstShare =
                    line.getCgstSharePercent() == null ? new BigDecimal("50") : line.getCgstSharePercent();
            BigDecimal sgstShare =
                    line.getSgstSharePercent() == null ? new BigDecimal("50") : line.getSgstSharePercent();
            line.setCgstRate(rate.multiply(cgstShare).divide(hundred, 4, RoundingMode.HALF_UP));
            line.setSgstRate(rate.multiply(sgstShare).divide(hundred, 4, RoundingMode.HALF_UP));
            line.setIgstRate(BigDecimal.ZERO);
        } else if (gstResult.igst().signum() > 0 || gstResult.otherTax().signum() > 0) {
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
        return lines.stream()
                .filter(line -> isStockedProduct(byId.get(line.getProductId())))
                .toList();
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
