package com.flowledger.inventory.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.inventory.dto.InventoryDtos.*;
import com.flowledger.inventory.entity.*;
import com.flowledger.inventory.entity.InventoryTransaction.Type;
import com.flowledger.inventory.repository.*;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class InventoryService {
 private final InventoryTransactionRepository txns; private final InventoryBatchRepository batches; private final SerialNumberRepository serials; private final ProductRepository products;
 public InventoryService(InventoryTransactionRepository t,InventoryBatchRepository b,SerialNumberRepository s,ProductRepository p){txns=t;batches=b;serials=s;products=p;}
 @Transactional public InventoryTransaction postTransaction(PostTransaction d) {
  UUID org=TenantContext.getOrganizationId();
  if(d.idempotencyKey()!=null&&!d.idempotencyKey().isBlank()) {var existing=txns.findByOrganizationIdAndIdempotencyKey(org,d.idempotencyKey()); if(existing.isPresent()) return existing.get();}
  BigDecimal in=n(d.inward()),out=n(d.outward()); if(in.signum()<0||out.signum()<0||(in.signum()>0&&out.signum()>0)||in.signum()==0&&out.signum()==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Specify exactly one positive inward or outward quantity");
  InventoryTransaction e=new InventoryTransaction(); e.setOrganizationId(org);e.setTransactionType(d.type());e.setProductId(d.productId());e.setWarehouseId(d.warehouseId());e.setTransactionDate(d.transactionDate()==null?LocalDate.now():d.transactionDate());e.setInwardQty(in);e.setOutwardQty(out);e.setReferenceType(d.referenceType());e.setReferenceId(d.referenceId());e.setReferenceNumber(d.referenceNumber());e.setIdempotencyKey(blank(d.idempotencyKey()));e.setBatchNumber(blank(d.batchNumber()));e.setSerialNumber(blank(d.serialNumber()));e.setExpiryDate(d.expiryDate());e.setUnitCost(d.unitCost());e.setNotes(d.notes()); e=txns.save(e);
  updateBatchAndSerial(e); return e;
 }
 @Transactional(readOnly=true) public Stock getStock(UUID product,UUID warehouse){return new Stock(product,warehouse,txns.stockBalance(TenantContext.getOrganizationId(),product,warehouse));}
 @Transactional(readOnly=true) public List<Ledger> getStockLedger(UUID product,UUID warehouse,LocalDate from,LocalDate to){UUID org=TenantContext.getOrganizationId(); LocalDate start=from==null?LocalDate.of(1970,1,1):from,end=to==null?LocalDate.now():to; List<InventoryTransaction> rows=warehouse==null?txns.findByOrganizationIdAndProductIdAndTransactionDateBetweenOrderByTransactionDateAscCreatedAtAsc(org,product,start,end):txns.findByOrganizationIdAndProductIdAndWarehouseIdAndTransactionDateBetweenOrderByTransactionDateAscCreatedAtAsc(org,product,warehouse,start,end); BigDecimal running=BigDecimal.ZERO; List<Ledger> result=new ArrayList<>();for(var e:rows){running=running.add(e.getInwardQty()).subtract(e.getOutwardQty());result.add(new Ledger(e.getTransactionDate(),e.getTransactionType(),e.getReferenceNumber(),e.getInwardQty(),e.getOutwardQty(),running));}return result;}
 @Transactional public InventoryTransaction adjustStock(Adjustment d){BigDecimal q=d.quantity();if(q.signum()==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Adjustment cannot be zero");return postTransaction(new PostTransaction(Type.STOCK_ADJUSTMENT,d.productId(),d.warehouseId(),q.signum()>0?q:BigDecimal.ZERO,q.signum()<0?q.abs():BigDecimal.ZERO,"ADJUSTMENT",null,null,UUID.randomUUID().toString(),null,null,null,null,d.notes(),LocalDate.now()));}
 @Transactional public void transferStock(Transfer d){if(d.fromWarehouseId().equals(d.toWarehouseId()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Warehouses must differ");String key=UUID.randomUUID().toString();postTransaction(new PostTransaction(Type.STOCK_TRANSFER,d.productId(),d.fromWarehouseId(),BigDecimal.ZERO,d.quantity(),"STOCK_TRANSFER",null,null,key+"-OUT",null,null,null,null,d.notes(),LocalDate.now()));postTransaction(new PostTransaction(Type.STOCK_TRANSFER,d.productId(),d.toWarehouseId(),d.quantity(),BigDecimal.ZERO,"STOCK_TRANSFER",null,null,key+"-IN",null,null,null,null,d.notes(),LocalDate.now()));}
 @Transactional public InventoryTransaction openingStock(Adjustment d){return postTransaction(new PostTransaction(Type.OPENING_STOCK,d.productId(),d.warehouseId(),d.quantity(),BigDecimal.ZERO,"OPENING_STOCK",null,null,UUID.randomUUID().toString(),null,null,null,null,d.notes(),LocalDate.now()));}
 @Transactional(readOnly=true) public List<Alert> lowStockAlerts(boolean reorder){UUID org=TenantContext.getOrganizationId();return products.findAll().stream().filter(p->org.equals(p.getOrganizationId())&&"PRODUCT".equals(p.getItemType())).map(p->new Alert(p.getId(),p.getName(),txns.stockBalance(org,p.getId(),null),reorder?p.getReorderLevel():p.getMinimumStockLevel())).filter(a->a.available().compareTo(a.threshold())<=0).toList();}
 @Transactional
 public boolean postPurchase(UUID warehouseId, UUID productId, BigDecimal quantity, BigDecimal unitCost,
                             LocalDate date, UUID referenceId, String referenceNumber, String idempotencyKey) {
  InventoryTransaction existing = postTransaction(new PostTransaction(
          Type.PURCHASE, productId, warehouseId, quantity, BigDecimal.ZERO,
          "GOODS_RECEIPT", referenceId, referenceNumber, idempotencyKey,
          null, null, null, unitCost, null, date));
  return existing != null;
 }

 @Transactional
 public boolean postPurchaseReturn(UUID warehouseId, UUID productId, BigDecimal quantity, BigDecimal unitCost,
                                   LocalDate date, UUID referenceId, String referenceNumber, String idempotencyKey) {
  postTransaction(new PostTransaction(
          Type.PURCHASE_RETURN, productId, warehouseId, BigDecimal.ZERO, quantity,
          "PURCHASE_RETURN", referenceId, referenceNumber, idempotencyKey,
          null, null, null, unitCost, null, date));
  return true;
 }

 private void updateBatchAndSerial(InventoryTransaction e){BigDecimal delta=e.getInwardQty().subtract(e.getOutwardQty());if(e.getBatchNumber()!=null){InventoryBatch b=batches.findByOrganizationIdAndProductIdAndWarehouseIdAndBatchNumber(e.getOrganizationId(),e.getProductId(),e.getWarehouseId(),e.getBatchNumber()).orElseGet(InventoryBatch::new);if(b.getId()==null){b.setOrganizationId(e.getOrganizationId());b.setProductId(e.getProductId());b.setWarehouseId(e.getWarehouseId());b.setBatchNumber(e.getBatchNumber());b.setExpiryDate(e.getExpiryDate());b.setQuantity(BigDecimal.ZERO);}b.setQuantity(n(b.getQuantity()).add(delta));batches.save(b);}if(e.getSerialNumber()!=null){SerialNumber s=serials.findByOrganizationIdAndProductIdAndSerialNumber(e.getOrganizationId(),e.getProductId(),e.getSerialNumber()).orElseGet(SerialNumber::new);if(s.getId()==null){s.setOrganizationId(e.getOrganizationId());s.setProductId(e.getProductId());s.setSerialNumber(e.getSerialNumber());}s.setWarehouseId(e.getWarehouseId());s.setStatus(delta.signum()>=0?SerialNumber.Status.IN_STOCK:SerialNumber.Status.SOLD);serials.save(s);}}
 private static BigDecimal n(BigDecimal v){return v==null?BigDecimal.ZERO:v;} private static String blank(String v){return v==null||v.isBlank()?null:v;}
}
