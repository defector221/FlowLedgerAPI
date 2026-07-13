package com.flowledger.sales.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.sales.entity.*;
import com.flowledger.sales.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesDocumentService {

    private final QuotationRepository quotations;
    private final SalesOrderRepository orders;
    private final DeliveryChallanRepository challans;
    private final SalesInvoiceRepository invoices;
    private final OrganizationRepository organizations;
    private final DocumentNumberService numbers;
    private final SalesInvoiceService invoiceService;

    public SalesDocumentService(
            QuotationRepository quotations,
            SalesOrderRepository orders,
            DeliveryChallanRepository challans,
            SalesInvoiceRepository invoices,
            OrganizationRepository organizations,
            DocumentNumberService numbers,
            SalesInvoiceService invoiceService) {
        this.quotations = quotations;
        this.orders = orders;
        this.challans = challans;
        this.invoices = invoices;
        this.organizations = organizations;
        this.numbers = numbers;
        this.invoiceService = invoiceService;
    }

    @Transactional(readOnly = true)
    public List<Quotation> listQuotations() {
        return quotations.findByOrganizationIdOrderByQuotationDateDesc(orgId());
    }

    @Transactional(readOnly = true)
    public Quotation getQuotation(UUID id) {
        return quotations
                .findByIdAndOrganizationId(id, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quotation not found"));
    }

    @Transactional
    public SalesOrder convertQuotationToOrder(UUID quotationId) {
        Quotation q = getQuotation(quotationId);
        if (q.getConvertedToOrderId() != null) {
            return orders.findByIdAndOrganizationId(q.getConvertedToOrderId(), orgId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Converted order missing"));
        }
        Organization org = organization();
        SalesOrder order = new SalesOrder();
        order.setOrganizationId(orgId());
        order.setCustomerId(q.getCustomerId());
        order.setOrderDate(LocalDate.now());
        order.setQuotationId(q.getId());
        order.setBillingAddress(q.getBillingAddress());
        order.setShippingAddress(q.getShippingAddress());
        order.setPlaceOfSupply(q.getPlaceOfSupply());
        order.setTermsAndConditions(q.getTermsAndConditions());
        order.setNotes(q.getNotes());
        order.setOrderNumber(numbers.next(
                org.getId(),
                "SALES_ORDER",
                org.getSalesOrderPrefix(),
                "{PREFIX}/{FY}/{SEQ:6}",
                org.getFinancialYearStart(),
                order.getOrderDate()));
        order.setSubtotal(q.getSubtotal());
        order.setDiscountTotal(q.getDiscountTotal());
        order.setTaxTotal(q.getTaxTotal());
        order.setGrandTotal(q.getGrandTotal());
        order.setStatus(SalesOrder.Status.CONFIRMED);
        int i = 0;
        for (QuotationItem qi : q.getItems()) {
            SalesOrderItem item = new SalesOrderItem();
            item.setSalesOrder(order);
            item.setProductId(qi.getProductId());
            item.setDescription(qi.getDescription());
            item.setHsnSacCode(qi.getHsnSacCode());
            item.setQuantity(qi.getQuantity());
            item.setUnitId(qi.getUnitId());
            item.setRate(qi.getRate());
            item.setDiscountPercent(qi.getDiscountPercent());
            item.setDiscountAmount(qi.getDiscountAmount());
            item.setTaxRate(qi.getTaxRate());
            item.setTaxableAmount(qi.getTaxableAmount());
            item.setCgstAmount(qi.getCgstAmount());
            item.setSgstAmount(qi.getSgstAmount());
            item.setIgstAmount(qi.getIgstAmount());
            item.setLineTotal(qi.getLineTotal());
            item.setLineOrder(i++);
            order.getItems().add(item);
        }
        SalesOrder saved = orders.save(order);
        q.setConvertedToOrderId(saved.getId());
        q.setStatus(Quotation.Status.CONVERTED);
        quotations.save(q);
        return saved;
    }

    @Transactional
    public DeliveryChallan convertOrderToChallan(UUID orderId, UUID warehouseId) {
        SalesOrder order = orders.findByIdAndOrganizationId(orderId, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales order not found"));
        challans.findByOrganizationIdAndSalesOrderId(orgId(), orderId).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Challan already exists for order");
        });
        Organization org = organization();
        DeliveryChallan challan = new DeliveryChallan();
        challan.setOrganizationId(orgId());
        challan.setCustomerId(order.getCustomerId());
        challan.setSalesOrderId(order.getId());
        challan.setWarehouseId(warehouseId);
        challan.setChallanDate(LocalDate.now());
        challan.setStatus(DeliveryChallan.Status.DELIVERED);
        challan.setChallanNumber(numbers.next(
                org.getId(),
                "DELIVERY_CHALLAN",
                org.getDeliveryChallanPrefix(),
                "{PREFIX}/{FY}/{SEQ:6}",
                org.getFinancialYearStart(),
                challan.getChallanDate()));
        int i = 0;
        for (SalesOrderItem oi : order.getItems()) {
            DeliveryChallanItem item = new DeliveryChallanItem();
            item.setDeliveryChallan(challan);
            item.setProductId(oi.getProductId());
            item.setDescription(oi.getDescription());
            item.setQuantity(oi.getQuantity());
            item.setUnitId(oi.getUnitId());
            item.setLineOrder(i++);
            challan.getItems().add(item);
        }
        return challans.save(challan);
    }

    @Transactional
    public SalesInvoice convertOrderToInvoice(UUID orderId, UUID warehouseId) {
        SalesOrder order = orders.findByIdAndOrganizationId(orderId, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sales order not found"));
        return invoices.findByOrganizationIdAndSalesOrderIdAndDeliveryChallanIdIsNull(orgId(), orderId)
                .orElseGet(() -> createInvoiceFromOrder(order, warehouseId, null));
    }

    @Transactional
    public SalesInvoice convertChallanToInvoice(UUID challanId) {
        DeliveryChallan challan = challans.findByIdAndOrganizationId(challanId, orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challan not found"));
        return invoices.findByOrganizationIdAndDeliveryChallanId(orgId(), challanId)
                .orElseGet(() -> {
                    SalesOrder order = challan.getSalesOrderId() == null
                            ? null
                            : orders.findByIdAndOrganizationId(challan.getSalesOrderId(), orgId())
                                    .orElse(null);
                    if (order == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challan has no linked sales order");
                    }
                    return createInvoiceFromOrder(order, challan.getWarehouseId(), challan.getId());
                });
    }

    private SalesInvoice createInvoiceFromOrder(SalesOrder order, UUID warehouseId, UUID challanId) {
        Organization org = organization();
        var requestItems = order.getItems().stream()
                .map(i -> new com.flowledger.sales.dto.SalesDtos.Item(
                        i.getProductId(),
                        i.getDescription(),
                        i.getHsnSacCode(),
                        i.getQuantity(),
                        i.getUnitId(),
                        i.getRate(),
                        i.getDiscountPercent(),
                        i.getTaxRate()))
                .toList();
        var request = new com.flowledger.sales.dto.SalesDtos.Invoice(
                order.getCustomerId(),
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                warehouseId,
                order.getId(),
                challanId,
                order.getBillingAddress(),
                order.getShippingAddress(),
                order.getPlaceOfSupply(),
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                order.getNotes(),
                order.getTermsAndConditions(),
                requestItems);
        SalesInvoice draft = invoiceService.createDraft(request);
        return invoiceService.confirm(draft.getId());
    }

    private UUID orgId() {
        return TenantContext.getOrganizationId();
    }

    private Organization organization() {
        return organizations
                .findById(orgId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }
}
