package com.flowledger.report.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.report.dto.ReportFilter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ReportService {
    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> report(String name, ReportFilter f) {
        UUID org = TenantContext.getOrganizationId();
        LocalDate from = f.from() == null ? LocalDate.now().minusMonths(12) : f.from();
        LocalDate to = f.to() == null ? LocalDate.now() : f.to();
        String sql =
                switch (name) {
                    case "sales", "gstr1" ->
                        "select invoice_date as date,invoice_number,subtotal,discount_total,grand_total,cgst_total,sgst_total,igst_total from sales_invoices where organization_id=:org and status='CONFIRMED' and invoice_date between :from and :to";
                    case "purchase" ->
                        "select invoice_date as date,invoice_number,subtotal,discount_total,grand_total,cgst_total,sgst_total,igst_total from purchase_invoices where organization_id=:org and status in ('CONFIRMED','PAID') and invoice_date between :from and :to";
                    case "outstanding-receivables" ->
                        "select invoice_number,invoice_date,outstanding_amount from sales_invoices where organization_id=:org and outstanding_amount>0";
                    case "outstanding-payables" ->
                        "select invoice_number,invoice_date,outstanding_amount from purchase_invoices where organization_id=:org and outstanding_amount>0";
                    case "stock-ledger" ->
                        "select transaction_date,transaction_type,reference_number,inward_qty,outward_qty,unit_cost from inventory_transactions where organization_id=:org and transaction_date between :from and :to";
                    case "stock-summary", "inventory-valuation" ->
                        "select product_id,warehouse_id,sum(inward_qty-outward_qty) as quantity,sum((inward_qty-outward_qty)*coalesce(unit_cost,0)) as value from inventory_transactions where organization_id=:org group by product_id,warehouse_id";
                    case "hsn", "product-sales" ->
                        "select product_id,sum(quantity) as quantity,sum(discount_amount) as discount,sum(line_total) as amount from sales_invoice_items where sales_invoice_id in (select id from sales_invoices where organization_id=:org and status='CONFIRMED' and invoice_date between :from and :to) group by product_id";
                    case "customer-statement" ->
                        "select customer_id,invoice_number,invoice_date,grand_total,amount_paid,outstanding_amount from sales_invoices where organization_id=:org and invoice_date between :from and :to";
                    case "supplier-statement" ->
                        "select supplier_id,invoice_number,invoice_date,grand_total,amount_paid,outstanding_amount from purchase_invoices where organization_id=:org and invoice_date between :from and :to";
                    case "profit-summary" ->
                        """
                        select
                          (select coalesce(sum(subtotal),0) from sales_invoices where organization_id=:org and status='CONFIRMED' and invoice_date between :from and :to) as gross_sales,
                          (select coalesce(sum(discount_total),0) from sales_invoices where organization_id=:org and status='CONFIRMED' and invoice_date between :from and :to) as sales_discount,
                          (select coalesce(sum(taxable_amount),0) from sales_invoices where organization_id=:org and status='CONFIRMED' and invoice_date between :from and :to) as net_sales,
                          (select coalesce(sum(grand_total),0) from sales_invoices where organization_id=:org and status='CONFIRMED' and invoice_date between :from and :to) as sales_total,
                          (select coalesce(sum(discount_total),0) from purchase_invoices where organization_id=:org and status in ('CONFIRMED','PAID') and invoice_date between :from and :to) as purchase_discount,
                          (select coalesce(sum(grand_total),0) from purchase_invoices where organization_id=:org and status in ('CONFIRMED','PAID') and invoice_date between :from and :to) as purchases,
                          (select coalesce(sum(taxable_amount),0) from sales_invoices where organization_id=:org and status='CONFIRMED' and invoice_date between :from and :to)
                            - (select coalesce(sum(taxable_amount),0) from purchase_invoices where organization_id=:org and status in ('CONFIRMED','PAID') and invoice_date between :from and :to) as gross_profit
                        """;
                    default -> throw new IllegalArgumentException("Unsupported report: " + name);
                };
        Query q = em.createNativeQuery(sql).setParameter("org", org);
        if (sql.contains(":from")) {
            q.setParameter("from", from).setParameter("to", to);
        }
        List<Object[]> rows = q.getResultList();
        List<String> cols = columns(name);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < row.length; i++) {
                m.put(i < cols.size() ? cols.get(i) : "value" + i, row[i]);
            }
            result.add(m);
        }
        return result;
    }

    private List<String> columns(String n) {
        return switch (n) {
            case "sales", "purchase", "gstr1" ->
                List.of("date", "number", "subtotal", "discount", "grandTotal", "cgst", "sgst", "igst");
            case "stock-ledger" -> List.of("date", "type", "reference", "inward", "outward", "unitCost");
            case "stock-summary", "inventory-valuation" -> List.of("productId", "warehouseId", "quantity", "value");
            case "hsn", "product-sales" -> List.of("productId", "quantity", "discount", "amount");
            case "profit-summary" ->
                List.of(
                        "grossSales",
                        "salesDiscount",
                        "netSales",
                        "salesTotal",
                        "purchaseDiscount",
                        "purchases",
                        "grossProfit");
            case "outstanding-receivables", "outstanding-payables" -> List.of("number", "date", "outstanding");
            default -> List.of("partyId", "number", "date", "grandTotal", "amountPaid", "outstanding");
        };
    }
}
