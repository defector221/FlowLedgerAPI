package com.flowledger.purchase.service;

import com.flowledger.accounting.service.AccountingPostingService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.purchase.dto.PurchaseDtos.Line;
import com.flowledger.purchase.dto.PurchaseDtos.ReturnRequest;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.purchase.entity.PurchaseReturn;
import com.flowledger.purchase.entity.PurchaseReturnItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PurchaseReturnService {
    @PersistenceContext
    private EntityManager em;

    private final PurchaseInvoiceService invoices;
    private final DocumentNumberService numbers;
    private final OrganizationRepository organizations;
    private final InventoryService inventory;
    private final AccountingPostingService accounting;

    public PurchaseReturnService(
            PurchaseInvoiceService invoices,
            DocumentNumberService numbers,
            OrganizationRepository organizations,
            InventoryService inventory,
            AccountingPostingService accounting) {
        this.invoices = invoices;
        this.numbers = numbers;
        this.organizations = organizations;
        this.inventory = inventory;
        this.accounting = accounting;
    }

    public PurchaseReturn create(ReturnRequest request) {
        PurchaseInvoice invoice = invoices.get(request.purchaseInvoiceId());
        if ("CANCELLED".equals(invoice.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot return a cancelled invoice");
        PurchaseReturn pr = new PurchaseReturn();
        pr.setOrganizationId(TenantContext.getOrganizationId());
        pr.setPurchaseInvoiceId(invoice.getId());
        pr.setSupplierId(invoice.getSupplierId());
        pr.setReturnDate(request.returnDate());
        pr.setNotes(request.notes());
        pr.setReturnNumber(number(request.returnDate()));
        BigDecimal total = BigDecimal.ZERO;
        int i = 0;
        for (Line line : request.items()) {
            PurchaseReturnItem item = new PurchaseReturnItem();
            item.setPurchaseReturn(pr);
            item.setProductId(line.productId());
            item.setQuantity(line.quantity());
            item.setRate(line.rate());
            item.setLineTotal(line.quantity().multiply(line.rate()).setScale(2, RoundingMode.HALF_UP));
            item.setLineOrder(i++);
            total = total.add(item.getLineTotal());
            pr.getItems().add(item);
        }
        pr.setGrandTotal(total);
        em.persist(pr);
        return pr;
    }

    public PurchaseReturn confirm(UUID id) {
        PurchaseReturn pr = get(id);
        if ("CANCELLED".equals(pr.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled return cannot be confirmed");
        if ("CONFIRMED".equals(pr.getStatus()) && pr.isInventoryPosted()) return pr;
        PurchaseInvoice invoice = invoices.get(pr.getPurchaseInvoiceId());
        if (invoice.getWarehouseId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice has no warehouse for stock return");
        if (!pr.isInventoryPosted()) {
            for (PurchaseReturnItem line : pr.getItems())
                inventory.postPurchaseReturn(
                        invoice.getWarehouseId(),
                        line.getProductId(),
                        line.getQuantity(),
                        line.getRate(),
                        pr.getReturnDate(),
                        pr.getId(),
                        pr.getReturnNumber(),
                        "purchase-return:" + pr.getId() + ":" + line.getId());
            pr.setInventoryPosted(true);
        }
        pr.setStatus("CONFIRMED");
        accounting.postPurchaseReturn(pr);
        return pr;
    }

    public PurchaseReturn get(UUID id) {
        PurchaseReturn pr = em.find(PurchaseReturn.class, id);
        if (pr == null || !pr.getOrganizationId().equals(TenantContext.getOrganizationId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase return not found");
        return pr;
    }

    public List<PurchaseReturn> list() {
        return em.createQuery(
                        "from PurchaseReturn p where p.organizationId=:org order by p.createdAt desc",
                        PurchaseReturn.class)
                .setParameter("org", TenantContext.getOrganizationId())
                .getResultList();
    }

    private String number(LocalDate d) {
        Organization o =
                organizations.findById(TenantContext.getOrganizationId()).orElseThrow();
        return numbers.next(o.getId(), "PURCHASE_RETURN", "PR", "{PREFIX}/{FY}/{SEQ:6}", o.getFinancialYearStart(), d);
    }
}
