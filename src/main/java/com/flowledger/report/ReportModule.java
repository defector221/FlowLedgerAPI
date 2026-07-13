package com.flowledger.report;

import com.flowledger.common.tenant.TenantContext;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

record ReportFilter(
        LocalDate from, LocalDate to, UUID customer, UUID supplier, UUID product, UUID category, UUID warehouse) {}

@Service
class ReportService {
    @PersistenceContext
    EntityManager em;

    List<Map<String, Object>> report(String name, ReportFilter f) {
        UUID org = TenantContext.getOrganizationId();
        LocalDate from = f.from() == null ? LocalDate.now().minusMonths(12) : f.from(),
                to = f.to() == null ? LocalDate.now() : f.to();
        String sql =
                switch (name) {
                    case "sales", "gstr1" ->
                        "select invoice_date as date,invoice_number,grand_total,cgst_total,sgst_total,igst_total from sales_invoices where organization_id=:org and invoice_date between :from and :to";
                    case "purchase" ->
                        "select invoice_date as date,invoice_number,grand_total,cgst_total,sgst_total,igst_total from purchase_invoices where organization_id=:org and invoice_date between :from and :to";
                    case "outstanding-receivables" ->
                        "select invoice_number,invoice_date,outstanding_amount from sales_invoices where organization_id=:org and outstanding_amount>0";
                    case "outstanding-payables" ->
                        "select invoice_number,invoice_date,outstanding_amount from purchase_invoices where organization_id=:org and outstanding_amount>0";
                    case "stock-ledger" ->
                        "select transaction_date,transaction_type,reference_number,inward_qty,outward_qty,unit_cost from inventory_transactions where organization_id=:org and transaction_date between :from and :to";
                    case "stock-summary", "inventory-valuation" ->
                        "select product_id,warehouse_id,sum(inward_qty-outward_qty) as quantity,sum((inward_qty-outward_qty)*coalesce(unit_cost,0)) as value from inventory_transactions where organization_id=:org group by product_id,warehouse_id";
                    case "hsn", "product-sales" ->
                        "select product_id,sum(quantity) as quantity,sum(line_total) as amount from sales_invoice_items where sales_invoice_id in (select id from sales_invoices where organization_id=:org and invoice_date between :from and :to) group by product_id";
                    case "customer-statement" ->
                        "select customer_id,invoice_number,invoice_date,grand_total,amount_paid,outstanding_amount from sales_invoices where organization_id=:org and invoice_date between :from and :to";
                    case "supplier-statement" ->
                        "select supplier_id,invoice_number,invoice_date,grand_total,amount_paid,outstanding_amount from purchase_invoices where organization_id=:org and invoice_date between :from and :to";
                    case "profit-summary" ->
                        "select (select coalesce(sum(grand_total),0) from sales_invoices where organization_id=:org and invoice_date between :from and :to) as sales,(select coalesce(sum(grand_total),0) from purchase_invoices where organization_id=:org and invoice_date between :from and :to) as purchases";
                    default -> throw new IllegalArgumentException("Unsupported report: " + name);
                };
        Query q = em.createNativeQuery(sql).setParameter("org", org);
        if (sql.contains(":from")) q.setParameter("from", from).setParameter("to", to);
        List<Object[]> rows = q.getResultList();
        List<String> cols = columns(name);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < row.length; i++) m.put(i < cols.size() ? cols.get(i) : "value" + i, row[i]);
            result.add(m);
        }
        return result;
    }

    private List<String> columns(String n) {
        return switch (n) {
            case "sales", "purchase", "gstr1" -> List.of("date", "number", "grandTotal", "cgst", "sgst", "igst");
            case "stock-ledger" -> List.of("date", "type", "reference", "inward", "outward", "unitCost");
            case "stock-summary", "inventory-valuation" -> List.of("productId", "warehouseId", "quantity", "value");
            case "hsn", "product-sales" -> List.of("productId", "quantity", "amount");
            default -> List.of("partyId", "number", "date", "grandTotal", "amountPaid", "outstanding");
        };
    }
}

@RestController
@RequestMapping("/api/v1/reports")
class ReportController {
    private final ReportService reports;

    ReportController(ReportService r) {
        reports = r;
    }

    @GetMapping("/{name}")
    List<Map<String, Object>> get(
            @PathVariable String name,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID customer,
            @RequestParam(required = false) UUID supplier,
            @RequestParam(required = false) UUID product,
            @RequestParam(required = false) UUID category,
            @RequestParam(required = false) UUID warehouse) {
        return reports.report(name, new ReportFilter(from, to, customer, supplier, product, category, warehouse));
    }

    @GetMapping(value = "/{name}/export", produces = "text/csv")
    ResponseEntity<String> csv(
            @PathVariable String name,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        List<Map<String, Object>> rows = reports.report(name, new ReportFilter(from, to, null, null, null, null, null));
        StringBuilder csv = new StringBuilder();
        if (!rows.isEmpty()) {
            csv.append(String.join(",", rows.get(0).keySet())).append('\n');
            for (Map<String, Object> r : rows)
                csv.append(r.values().stream()
                                .map(v -> "\"" + String.valueOf(v).replace("\"", "\"\"") + "\"")
                                .collect(java.util.stream.Collectors.joining(",")))
                        .append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + name + ".csv")
                .body(csv.toString());
    }
}
