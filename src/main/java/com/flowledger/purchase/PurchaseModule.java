package com.flowledger.purchase;

import com.flowledger.common.entity.AuditedEntity;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.tax.dto.GstCalculationDtos;
import com.flowledger.tax.service.GstCalculationService;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Entity @Table(name="purchase_orders") @Getter @Setter @NoArgsConstructor
class PurchaseOrder extends AuditedEntity {
 @Column(name="po_number") String poNumber; @Column(name="supplier_id") UUID supplierId; @Column(name="order_date") LocalDate orderDate;
 LocalDate expectedDeliveryDate; String status="DRAFT"; BigDecimal subtotal=BigDecimal.ZERO,discountTotal=BigDecimal.ZERO,taxTotal=BigDecimal.ZERO,grandTotal=BigDecimal.ZERO;
 @Column(columnDefinition="text") String termsAndConditions,notes; @Version Long version;
 @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("lineOrder") List<PurchaseOrderItem> items=new ArrayList<>();
}
@Entity @Table(name="purchase_order_items") @Getter @Setter @NoArgsConstructor
class PurchaseOrderItem {
 @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="purchase_order_id") PurchaseOrder order;
 UUID productId,unitId; String description,hsnSacCode; BigDecimal quantity,rate,discountPercent=BigDecimal.ZERO,discountAmount=BigDecimal.ZERO,taxRate=BigDecimal.ZERO,taxableAmount=BigDecimal.ZERO,cgstAmount=BigDecimal.ZERO,sgstAmount=BigDecimal.ZERO,igstAmount=BigDecimal.ZERO,lineTotal=BigDecimal.ZERO; Integer lineOrder=0;
}
@Entity @Table(name="goods_receipts") @Getter @Setter @NoArgsConstructor
class GoodsReceipt extends AuditedEntity {
 @Column(name="grn_number") String grnNumber; UUID supplierId,purchaseOrderId,warehouseId; @Column(name="receipt_date") LocalDate receiptDate; String status="DRAFT"; boolean inventoryPosted; @Column(columnDefinition="text") String notes; @Version Long version;
 @OneToMany(mappedBy="receipt",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("lineOrder") List<GoodsReceiptItem> items=new ArrayList<>();
}
@Entity @Table(name="goods_receipt_items") @Getter @Setter @NoArgsConstructor
class GoodsReceiptItem {
 @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="goods_receipt_id") GoodsReceipt receipt;
 UUID productId,unitId; String description,batchNumber; LocalDate expiryDate; BigDecimal quantity; Integer lineOrder=0;
}
@Entity @Table(name="purchase_invoices") @Getter @Setter @NoArgsConstructor
class PurchaseInvoice extends AuditedEntity {
 @Column(name="invoice_number") String invoiceNumber; String supplierInvoiceNumber; LocalDate invoiceDate,dueDate; UUID supplierId,purchaseOrderId,goodsReceiptId,warehouseId; String placeOfSupply,supplierGstin,status="DRAFT",paymentStatus="UNPAID"; boolean reverseCharge,taxInclusive;
 BigDecimal subtotal=BigDecimal.ZERO,discountTotal=BigDecimal.ZERO,taxableAmount=BigDecimal.ZERO,cgstTotal=BigDecimal.ZERO,sgstTotal=BigDecimal.ZERO,igstTotal=BigDecimal.ZERO,cessTotal=BigDecimal.ZERO,shippingCharges=BigDecimal.ZERO,additionalCharges=BigDecimal.ZERO,roundOff=BigDecimal.ZERO,grandTotal=BigDecimal.ZERO,amountPaid=BigDecimal.ZERO,outstandingAmount=BigDecimal.ZERO; @Column(columnDefinition="text") String notes,termsAndConditions; @Version Long version;
 @OneToMany(mappedBy="invoice",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("lineOrder") List<PurchaseInvoiceItem> items=new ArrayList<>();
}
@Entity @Table(name="purchase_invoice_items") @Getter @Setter @NoArgsConstructor
class PurchaseInvoiceItem {
 @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id; @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="purchase_invoice_id") PurchaseInvoice invoice;
 UUID productId,unitId; String description,hsnSacCode; BigDecimal quantity,rate,discountPercent=BigDecimal.ZERO,discountAmount=BigDecimal.ZERO,taxRate=BigDecimal.ZERO,taxableAmount=BigDecimal.ZERO,cgstRate=BigDecimal.ZERO,sgstRate=BigDecimal.ZERO,igstRate=BigDecimal.ZERO,cgstAmount=BigDecimal.ZERO,sgstAmount=BigDecimal.ZERO,igstAmount=BigDecimal.ZERO,cessAmount=BigDecimal.ZERO,lineTotal=BigDecimal.ZERO; Integer lineOrder=0;
}
@Entity @Table(name="purchase_returns") @Getter @Setter @NoArgsConstructor class PurchaseReturn extends AuditedEntity { @Column(name="return_number") String returnNumber; UUID purchaseInvoiceId,supplierId; LocalDate returnDate; String status="DRAFT"; BigDecimal grandTotal=BigDecimal.ZERO; boolean inventoryPosted; @Column(columnDefinition="text") String notes; @Version Long version; @OneToMany(mappedBy="purchaseReturn",cascade=CascadeType.ALL,orphanRemoval=true) List<PurchaseReturnItem> items=new ArrayList<>(); }
@Entity @Table(name="purchase_return_items") @Getter @Setter @NoArgsConstructor class PurchaseReturnItem { @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id; @ManyToOne @JoinColumn(name="purchase_return_id") PurchaseReturn purchaseReturn; UUID productId; BigDecimal quantity,rate,lineTotal; Integer lineOrder=0; }
@Entity @Table(name="debit_notes") @Getter @Setter @NoArgsConstructor class DebitNote extends AuditedEntity { @Column(name="debit_note_number") String debitNoteNumber; UUID purchaseReturnId,purchaseInvoiceId,supplierId; LocalDate debitNoteDate; BigDecimal amount; String status="ISSUED"; @Column(columnDefinition="text") String notes; }

record Line(@NotNull UUID productId, UUID unitId, String description, String hsnSacCode, @NotNull @DecimalMin("0.0001") BigDecimal quantity, @NotNull @DecimalMin("0") BigDecimal rate, BigDecimal discountPercent, BigDecimal taxRate) {}
record OrderRequest(@NotNull UUID supplierId, @NotNull LocalDate orderDate, LocalDate expectedDeliveryDate, String notes, String termsAndConditions, @NotEmpty List<@Valid Line> items) {}
record GrnRequest(@NotNull UUID warehouseId, @NotNull LocalDate receiptDate, String notes, List<@Valid Line> items) {}
record InvoiceRequest(String supplierInvoiceNumber, @NotNull LocalDate invoiceDate, LocalDate dueDate, String placeOfSupply, Boolean taxInclusive, String notes, List<@Valid Line> items) {}
record ReturnRequest(@NotNull UUID purchaseInvoiceId, @NotNull LocalDate returnDate, String notes, @NotEmpty List<@Valid Line> items) {}

@Service @Transactional
class PurchaseOrderService {
 @PersistenceContext EntityManager em; private final DocumentNumberService numbers; private final OrganizationRepository organizations;
 PurchaseOrderService(DocumentNumberService n,OrganizationRepository o){numbers=n;organizations=o;}
 PurchaseOrder create(OrderRequest request){ PurchaseOrder po=new PurchaseOrder(); po.setOrganizationId(TenantContext.getOrganizationId()); po.setSupplierId(request.supplierId()); po.setOrderDate(request.orderDate()); po.setExpectedDeliveryDate(request.expectedDeliveryDate()); po.setNotes(request.notes()); po.setTermsAndConditions(request.termsAndConditions()); po.setPoNumber(number("PURCHASE_ORDER","PO",request.orderDate())); applyOrderLines(po,request.items()); em.persist(po); return po; }
 PurchaseOrder get(UUID id){PurchaseOrder found=em.find(PurchaseOrder.class,id);if(found==null)throw missing("Purchase order");return owned(found);}
 List<PurchaseOrder> list(){return em.createQuery("from PurchaseOrder p where p.organizationId=:org order by p.createdAt desc",PurchaseOrder.class).setParameter("org",TenantContext.getOrganizationId()).getResultList();}
 PurchaseOrder update(UUID id,OrderRequest request){PurchaseOrder po=get(id); if(!"DRAFT".equals(po.getStatus()))throw conflict("Only draft orders can be changed"); po.setSupplierId(request.supplierId());po.setOrderDate(request.orderDate());po.setExpectedDeliveryDate(request.expectedDeliveryDate());po.setNotes(request.notes());po.setTermsAndConditions(request.termsAndConditions());po.getItems().clear();applyOrderLines(po,request.items());return po;}
 void delete(UUID id){PurchaseOrder po=get(id);if(!"DRAFT".equals(po.getStatus()))throw conflict("Only draft orders can be deleted");em.remove(po);}
 private void applyOrderLines(PurchaseOrder po,List<Line> lines){int i=0;for(Line line:lines){PurchaseOrderItem item=new PurchaseOrderItem();item.setOrder(po); item.setProductId(line.productId());item.setUnitId(line.unitId());item.setDescription(line.description());item.setHsnSacCode(line.hsnSacCode());item.setQuantity(line.quantity());item.setRate(line.rate());item.setDiscountPercent(z(line.discountPercent()));item.setTaxRate(z(line.taxRate()));item.setLineOrder(i++); calculate(item);po.getItems().add(item);} totals(po);}
 private void calculate(PurchaseOrderItem x){BigDecimal gross=x.getQuantity().multiply(x.getRate());x.setDiscountAmount(gross.multiply(x.getDiscountPercent()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP));x.setTaxableAmount(gross.subtract(x.getDiscountAmount()));BigDecimal tax=x.getTaxableAmount().multiply(x.getTaxRate()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);x.setCgstAmount(tax.divide(BigDecimal.valueOf(2),2,RoundingMode.HALF_UP));x.setSgstAmount(tax.subtract(x.getCgstAmount()));x.setIgstAmount(BigDecimal.ZERO);x.setLineTotal(x.getTaxableAmount().add(tax));}
 private void totals(PurchaseOrder po){po.setSubtotal(po.getItems().stream().map(i->i.getQuantity().multiply(i.getRate())).reduce(BigDecimal.ZERO,BigDecimal::add));po.setDiscountTotal(po.getItems().stream().map(PurchaseOrderItem::getDiscountAmount).reduce(BigDecimal.ZERO,BigDecimal::add));po.setTaxTotal(po.getItems().stream().map(i->i.getCgstAmount().add(i.getSgstAmount()).add(i.getIgstAmount())).reduce(BigDecimal.ZERO,BigDecimal::add));po.setGrandTotal(po.getItems().stream().map(PurchaseOrderItem::getLineTotal).reduce(BigDecimal.ZERO,BigDecimal::add));}
 private String number(String type,String prefix,LocalDate date){Organization o=organizations.findById(TenantContext.getOrganizationId()).orElseThrow();return numbers.next(o.getId(),type,prefix,"{PREFIX}/{FY}/{SEQ:6}",o.getFinancialYearStart(),date);}
 private PurchaseOrder owned(PurchaseOrder p){if(!p.getOrganizationId().equals(TenantContext.getOrganizationId()))throw missing("Purchase order");return p;}
 private ResponseStatusException missing(String s){return new ResponseStatusException(HttpStatus.NOT_FOUND,s+" not found");}
 private ResponseStatusException conflict(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);}
 private static BigDecimal z(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
}

@Service @Transactional
class GoodsReceiptService {
 @PersistenceContext EntityManager em; private final PurchaseOrderService orders;private final DocumentNumberService numbers;private final OrganizationRepository organizations;private final InventoryService inventory;
 GoodsReceiptService(PurchaseOrderService o,DocumentNumberService n,OrganizationRepository r,InventoryService i){orders=o;numbers=n;organizations=r;inventory=i;}
 GoodsReceipt fromPurchaseOrder(UUID poId,GrnRequest r){PurchaseOrder po=orders.get(poId);GoodsReceipt existing=em.createQuery("from GoodsReceipt g where g.organizationId=:org and g.purchaseOrderId=:po",GoodsReceipt.class).setParameter("org",TenantContext.getOrganizationId()).setParameter("po",poId).getResultStream().findFirst().orElse(null);if(existing!=null)return existing;GoodsReceipt g=new GoodsReceipt();g.setOrganizationId(TenantContext.getOrganizationId());g.setPurchaseOrderId(poId);g.setSupplierId(po.getSupplierId());g.setWarehouseId(r.warehouseId());g.setReceiptDate(r.receiptDate());g.setNotes(r.notes());g.setGrnNumber(number(r.receiptDate()));List<Line> source=r.items()==null||r.items().isEmpty()?po.getItems().stream().map(x->new Line(x.getProductId(),x.getUnitId(),x.getDescription(),null,x.getQuantity(),x.getRate(),null,null)).toList():r.items();int ix=0;for(Line x:source){GoodsReceiptItem item=new GoodsReceiptItem();item.setReceipt(g);item.setProductId(x.productId());item.setUnitId(x.unitId());item.setDescription(x.description());item.setQuantity(x.quantity());item.setLineOrder(ix++);g.getItems().add(item);}em.persist(g);return g;}
 GoodsReceipt confirm(UUID id){GoodsReceipt g=get(id);if(g.isInventoryPosted())return g;for(GoodsReceiptItem item:g.getItems())inventory.postPurchase(g.getWarehouseId(),item.getProductId(),item.getQuantity(),BigDecimal.ZERO,g.getReceiptDate(),g.getId(),g.getGrnNumber(),"grn:"+g.getId()+":"+item.getId());g.setInventoryPosted(true);g.setStatus("CONFIRMED");return g;}
 GoodsReceipt get(UUID id){GoodsReceipt g=em.find(GoodsReceipt.class,id);if(g==null||!g.getOrganizationId().equals(TenantContext.getOrganizationId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"GRN not found");return g;}
 List<GoodsReceipt> list(){return em.createQuery("from GoodsReceipt g where g.organizationId=:org order by g.createdAt desc",GoodsReceipt.class).setParameter("org",TenantContext.getOrganizationId()).getResultList();}
 private String number(LocalDate d){Organization o=organizations.findById(TenantContext.getOrganizationId()).orElseThrow();return numbers.next(o.getId(),"GOODS_RECEIPT","GRN","{PREFIX}/{FY}/{SEQ:6}",o.getFinancialYearStart(),d);}
}

@Service @Transactional
class PurchaseInvoiceService {
 @PersistenceContext EntityManager em; private final GoodsReceiptService grns; private final PurchaseOrderService orders;private final DocumentNumberService numbers;private final OrganizationRepository organizations; private final GstCalculationService gst;
 PurchaseInvoiceService(GoodsReceiptService g,PurchaseOrderService o,DocumentNumberService n,OrganizationRepository r,GstCalculationService tax){grns=g;orders=o;numbers=n;organizations=r;gst=tax;}
 PurchaseInvoice fromGrn(UUID grnId,InvoiceRequest r){GoodsReceipt g=grns.get(grnId);PurchaseInvoice duplicate=em.createQuery("from PurchaseInvoice i where i.organizationId=:org and i.goodsReceiptId=:grn",PurchaseInvoice.class).setParameter("org",TenantContext.getOrganizationId()).setParameter("grn",grnId).getResultStream().findFirst().orElse(null);if(duplicate!=null)return duplicate;return create(g.getSupplierId(),g.getPurchaseOrderId(),grnId,g.getWarehouseId(),r,g.getItems().stream().map(x->new Line(x.getProductId(),x.getUnitId(),x.getDescription(),null,x.getQuantity(),BigDecimal.ZERO,null,null)).toList());}
 PurchaseInvoice fromPo(UUID poId,InvoiceRequest r){PurchaseOrder p=orders.get(poId);return create(p.getSupplierId(),poId,null,null,r,r.items()==null?p.getItems().stream().map(x->new Line(x.getProductId(),x.getUnitId(),x.getDescription(),x.getHsnSacCode(),x.getQuantity(),x.getRate(),x.getDiscountPercent(),x.getTaxRate())).toList():r.items());}
 PurchaseInvoice confirm(UUID id){PurchaseInvoice i=get(id);if(!"DRAFT".equals(i.getStatus()))return i;i.setStatus(i.getOutstandingAmount().signum()==0?"PAID":"CONFIRMED");return i;}
 PurchaseInvoice get(UUID id){PurchaseInvoice i=em.find(PurchaseInvoice.class,id);if(i==null||!i.getOrganizationId().equals(TenantContext.getOrganizationId()))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Purchase invoice not found");return i;}
 List<PurchaseInvoice> list(){return em.createQuery("from PurchaseInvoice i where i.organizationId=:org order by i.createdAt desc",PurchaseInvoice.class).setParameter("org",TenantContext.getOrganizationId()).getResultList();}
 private PurchaseInvoice create(UUID supplier,UUID po,UUID grn,UUID wh,InvoiceRequest r,List<Line> lines){if(lines==null||lines.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invoice requires items");PurchaseInvoice i=new PurchaseInvoice();i.setOrganizationId(TenantContext.getOrganizationId());i.setSupplierId(supplier);i.setPurchaseOrderId(po);i.setGoodsReceiptId(grn);i.setWarehouseId(wh);i.setInvoiceDate(r.invoiceDate());i.setDueDate(r.dueDate());i.setSupplierInvoiceNumber(r.supplierInvoiceNumber());i.setPlaceOfSupply(r.placeOfSupply());i.setTaxInclusive(Boolean.TRUE.equals(r.taxInclusive()));i.setNotes(r.notes());i.setInvoiceNumber(number(r.invoiceDate()));int n=0;for(Line l:lines){PurchaseInvoiceItem x=new PurchaseInvoiceItem();x.setInvoice(i);x.setProductId(l.productId());x.setUnitId(l.unitId());x.setDescription(l.description());x.setHsnSacCode(l.hsnSacCode());x.setQuantity(l.quantity());x.setRate(l.rate());x.setDiscountPercent(l.discountPercent()==null?BigDecimal.ZERO:l.discountPercent());x.setTaxRate(l.taxRate()==null?BigDecimal.ZERO:l.taxRate());x.setLineOrder(n++);BigDecimal discount=l.quantity().multiply(l.rate()).multiply(x.getDiscountPercent()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);String place=i.getPlaceOfSupply()==null?organization().getStateCode():i.getPlaceOfSupply();GstCalculationDtos.Response tax=gst.calculate(new GstCalculationDtos.Request(organization().getStateCode(),place,x.getTaxRate(),i.isTaxInclusive(),l.quantity(),l.rate(),discount));x.setDiscountAmount(discount);x.setTaxableAmount(tax.taxable());x.setCgstAmount(tax.cgst());x.setSgstAmount(tax.sgst());x.setIgstAmount(tax.igst());x.setLineTotal(tax.lineTotal());i.getItems().add(x);}i.setSubtotal(i.getItems().stream().map(x->x.getQuantity().multiply(x.getRate())).reduce(BigDecimal.ZERO,BigDecimal::add));i.setTaxableAmount(i.getItems().stream().map(PurchaseInvoiceItem::getTaxableAmount).reduce(BigDecimal.ZERO,BigDecimal::add));i.setCgstTotal(i.getItems().stream().map(PurchaseInvoiceItem::getCgstAmount).reduce(BigDecimal.ZERO,BigDecimal::add));i.setSgstTotal(i.getItems().stream().map(PurchaseInvoiceItem::getSgstAmount).reduce(BigDecimal.ZERO,BigDecimal::add));i.setIgstTotal(i.getItems().stream().map(PurchaseInvoiceItem::getIgstAmount).reduce(BigDecimal.ZERO,BigDecimal::add));i.setGrandTotal(i.getItems().stream().map(PurchaseInvoiceItem::getLineTotal).reduce(BigDecimal.ZERO,BigDecimal::add));i.setOutstandingAmount(i.getGrandTotal());em.persist(i);return i;}
 private Organization organization(){return organizations.findById(TenantContext.getOrganizationId()).orElseThrow();}private String number(LocalDate d){Organization o=organization();return numbers.next(o.getId(),"PURCHASE_INVOICE",o.getPurchaseInvoicePrefix(),"{PREFIX}/{FY}/{SEQ:6}",o.getFinancialYearStart(),d);}
}

@RestController @RequestMapping("/api/v1/purchases")
class PurchaseController {
 private final PurchaseOrderService orders;private final GoodsReceiptService grns;private final PurchaseInvoiceService invoices;
 PurchaseController(PurchaseOrderService o,GoodsReceiptService g,PurchaseInvoiceService i){orders=o;grns=g;invoices=i;}
 @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED) PurchaseOrder createOrder(@Valid @RequestBody OrderRequest r){return orders.create(r);}@GetMapping("/orders")List<PurchaseOrder> orders(){return orders.list();}@GetMapping("/orders/{id}")PurchaseOrder order(@PathVariable UUID id){return orders.get(id);}@PutMapping("/orders/{id}")PurchaseOrder updateOrder(@PathVariable UUID id,@Valid @RequestBody OrderRequest r){return orders.update(id,r);}@DeleteMapping("/orders/{id}")@ResponseStatus(HttpStatus.NO_CONTENT)void deleteOrder(@PathVariable UUID id){orders.delete(id);}
 @PostMapping("/grn/from-order/{poId}") @ResponseStatus(HttpStatus.CREATED) GoodsReceipt fromOrder(@PathVariable UUID poId,@Valid @RequestBody GrnRequest r){return grns.fromPurchaseOrder(poId,r);}@PostMapping("/grn/{id}/confirm")GoodsReceipt confirmGrn(@PathVariable UUID id){return grns.confirm(id);}@GetMapping("/grn")List<GoodsReceipt> grns(){return grns.list();}
 @PostMapping("/invoices/from-order/{poId}")PurchaseInvoice invoiceFromPo(@PathVariable UUID poId,@Valid @RequestBody InvoiceRequest r){return invoices.fromPo(poId,r);}@PostMapping("/invoices/from-grn/{grnId}")PurchaseInvoice invoiceFromGrn(@PathVariable UUID grnId,@Valid @RequestBody InvoiceRequest r){return invoices.fromGrn(grnId,r);}@PostMapping("/invoices/{id}/confirm")PurchaseInvoice confirmInvoice(@PathVariable UUID id){return invoices.confirm(id);}@GetMapping("/invoices")List<PurchaseInvoice> invoices(){return invoices.list();}
}
