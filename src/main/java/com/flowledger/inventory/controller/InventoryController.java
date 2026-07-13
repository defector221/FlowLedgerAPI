package com.flowledger.inventory.controller;
import com.flowledger.inventory.dto.InventoryDtos.*;
import com.flowledger.inventory.entity.InventoryTransaction;
import com.flowledger.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate; import java.util.*;
@RestController @RequestMapping("/api/v1/inventory")
public class InventoryController {
 private final InventoryService service; public InventoryController(InventoryService s){service=s;}
 @PostMapping("/transactions") @ResponseStatus(HttpStatus.CREATED) public InventoryTransaction post(@Valid @RequestBody PostTransaction d){return service.postTransaction(d);}
 @GetMapping("/stock/{productId}") public Stock stock(@PathVariable UUID productId,@RequestParam(required=false) UUID warehouseId){return service.getStock(productId,warehouseId);}
 @GetMapping("/ledger/{productId}") public List<Ledger> ledger(@PathVariable UUID productId,@RequestParam(required=false) UUID warehouseId,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to){return service.getStockLedger(productId,warehouseId,from,to);}
 @PostMapping("/adjustments") @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN','INVENTORY_MANAGER')") public InventoryTransaction adjust(@Valid @RequestBody Adjustment d){return service.adjustStock(d);}
 @PostMapping("/transfers") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN','INVENTORY_MANAGER')") public void transfer(@Valid @RequestBody Transfer d){service.transferStock(d);}
 @PostMapping("/opening-stock") public InventoryTransaction opening(@Valid @RequestBody Adjustment d){return service.openingStock(d);}
 @GetMapping("/alerts/low-stock") public List<Alert> low(){return service.lowStockAlerts(false);}
 @GetMapping("/alerts/reorder") public List<Alert> reorder(){return service.lowStockAlerts(true);}
}
