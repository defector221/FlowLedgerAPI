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
    public List<Map<String, Object>> report(String name, ReportFilter filter) {
        UUID org = TenantContext.getOrganizationId();
        LocalDate from = filter.from() == null ? LocalDate.now().minusMonths(12) : filter.from();
        LocalDate to = filter.to() == null ? LocalDate.now() : filter.to();
        String sql =
                switch (name) {
                    case "sales", "gstr1" ->
                        """
                        select i.invoice_date as date,
                               i.invoice_number as number,
                               coalesce(c.customer_name, '') as customer,
                               i.subtotal,
                               i.discount_total as discount,
                               i.cgst_total as cgst,
                               i.sgst_total as sgst,
                               i.igst_total as igst,
                               i.grand_total as "grandTotal"
                        from sales_invoices i
                        left join customers c on c.id = i.customer_id
                        where i.organization_id = :org
                          and i.status in ('CONFIRMED','PARTIALLY_PAID','PAID','OVERDUE')
                          and i.invoice_date between :from and :to
                        order by i.invoice_date desc, i.invoice_number
                        """;
                    case "purchase" ->
                        """
                        select i.invoice_date as date,
                               i.invoice_number as number,
                               coalesce(s.supplier_name, '') as supplier,
                               i.subtotal,
                               i.discount_total as discount,
                               i.cgst_total as cgst,
                               i.sgst_total as sgst,
                               i.igst_total as igst,
                               i.grand_total as "grandTotal"
                        from purchase_invoices i
                        left join suppliers s on s.id = i.supplier_id
                        where i.organization_id = :org
                          and i.status in ('CONFIRMED','PAID','PARTIALLY_PAID')
                          and i.invoice_date between :from and :to
                        order by i.invoice_date desc, i.invoice_number
                        """;
                    case "outstanding-receivables" ->
                        """
                        select i.invoice_number as number,
                               i.invoice_date as date,
                               coalesce(c.customer_name, '') as customer,
                               i.grand_total as "grandTotal",
                               i.amount_paid as "amountPaid",
                               i.outstanding_amount as outstanding
                        from sales_invoices i
                        left join customers c on c.id = i.customer_id
                        where i.organization_id = :org
                          and i.outstanding_amount > 0
                          and i.status <> 'CANCELLED'
                        order by i.invoice_date
                        """;
                    case "outstanding-payables" ->
                        """
                        select i.invoice_number as number,
                               i.invoice_date as date,
                               coalesce(s.supplier_name, '') as supplier,
                               i.grand_total as "grandTotal",
                               i.amount_paid as "amountPaid",
                               i.outstanding_amount as outstanding
                        from purchase_invoices i
                        left join suppliers s on s.id = i.supplier_id
                        where i.organization_id = :org
                          and i.outstanding_amount > 0
                          and i.status <> 'CANCELLED'
                        order by i.invoice_date
                        """;
                    case "stock-ledger" ->
                        """
                        select t.transaction_date as date,
                               t.transaction_type as type,
                               coalesce(p.name, '') as product,
                               coalesce(w.warehouse_name, '') as warehouse,
                               coalesce(t.reference_number, '') as reference,
                               t.inward_qty as inward,
                               t.outward_qty as outward,
                               coalesce(t.unit_cost, 0) as "unitCost"
                        from inventory_transactions t
                        left join products p on p.id = t.product_id
                        left join warehouses w on w.id = t.warehouse_id
                        where t.organization_id = :org
                          and t.transaction_date between :from and :to
                        order by t.transaction_date desc
                        """;
                    case "stock-summary", "inventory-valuation" ->
                        """
                        select coalesce(p.sku, '') as sku,
                               coalesce(p.name, 'Unknown product') as product,
                               coalesce(w.warehouse_code, '') as "warehouseCode",
                               coalesce(w.warehouse_name, 'Unknown warehouse') as warehouse,
                               sum(t.inward_qty - t.outward_qty) as quantity,
                               sum((t.inward_qty - t.outward_qty) * coalesce(t.unit_cost, 0)) as value
                        from inventory_transactions t
                        left join products p on p.id = t.product_id
                        left join warehouses w on w.id = t.warehouse_id
                        where t.organization_id = :org
                        group by p.sku, p.name, w.warehouse_code, w.warehouse_name
                        order by coalesce(p.name, ''), coalesce(w.warehouse_name, '')
                        """;
                    case "hsn", "product-sales" ->
                        """
                        select coalesce(p.sku, '') as sku,
                               coalesce(p.name, 'Unknown product') as product,
                               coalesce(p.hsn_sac_code, '') as hsn,
                               sum(li.quantity) as quantity,
                               sum(li.discount_amount) as discount,
                               sum(li.line_total) as amount
                        from sales_invoice_items li
                        join sales_invoices i on i.id = li.sales_invoice_id
                        left join products p on p.id = li.product_id
                        where i.organization_id = :org
                          and i.status in ('CONFIRMED','PARTIALLY_PAID','PAID','OVERDUE')
                          and i.invoice_date between :from and :to
                        group by p.sku, p.name, p.hsn_sac_code
                        order by amount desc
                        """;
                    case "customer-statement" ->
                        """
                        select coalesce(c.customer_name, '') as customer,
                               i.invoice_number as number,
                               i.invoice_date as date,
                               i.grand_total as "grandTotal",
                               i.amount_paid as "amountPaid",
                               i.outstanding_amount as outstanding
                        from sales_invoices i
                        left join customers c on c.id = i.customer_id
                        where i.organization_id = :org
                          and i.invoice_date between :from and :to
                        order by c.customer_name, i.invoice_date
                        """;
                    case "supplier-statement" ->
                        """
                        select coalesce(s.supplier_name, '') as supplier,
                               i.invoice_number as number,
                               i.invoice_date as date,
                               i.grand_total as "grandTotal",
                               i.amount_paid as "amountPaid",
                               i.outstanding_amount as outstanding
                        from purchase_invoices i
                        left join suppliers s on s.id = i.supplier_id
                        where i.organization_id = :org
                          and i.invoice_date between :from and :to
                        order by s.supplier_name, i.invoice_date
                        """;
                    case "profit-summary" ->
                        """
                        select
                          (select coalesce(sum(subtotal),0) from sales_invoices where organization_id=:org and status in ('CONFIRMED','PARTIALLY_PAID','PAID','OVERDUE') and invoice_date between :from and :to) as "grossSales",
                          (select coalesce(sum(discount_total),0) from sales_invoices where organization_id=:org and status in ('CONFIRMED','PARTIALLY_PAID','PAID','OVERDUE') and invoice_date between :from and :to) as "salesDiscount",
                          (select coalesce(sum(taxable_amount),0) from sales_invoices where organization_id=:org and status in ('CONFIRMED','PARTIALLY_PAID','PAID','OVERDUE') and invoice_date between :from and :to) as "netSales",
                          (select coalesce(sum(grand_total),0) from sales_invoices where organization_id=:org and status in ('CONFIRMED','PARTIALLY_PAID','PAID','OVERDUE') and invoice_date between :from and :to) as "salesTotal",
                          (select coalesce(sum(discount_total),0) from purchase_invoices where organization_id=:org and status in ('CONFIRMED','PAID','PARTIALLY_PAID') and invoice_date between :from and :to) as "purchaseDiscount",
                          (select coalesce(sum(grand_total),0) from purchase_invoices where organization_id=:org and status in ('CONFIRMED','PAID','PARTIALLY_PAID') and invoice_date between :from and :to) as purchases,
                          (select coalesce(sum(taxable_amount),0) from sales_invoices where organization_id=:org and status in ('CONFIRMED','PARTIALLY_PAID','PAID','OVERDUE') and invoice_date between :from and :to)
                            - (select coalesce(sum(taxable_amount),0) from purchase_invoices where organization_id=:org and status in ('CONFIRMED','PAID','PARTIALLY_PAID') and invoice_date between :from and :to) as "grossProfit"
                        """;
                    default -> throw new IllegalArgumentException("Unsupported report: " + name);
                };
        Query nativeQuery = em.createNativeQuery(sql).setParameter("org", org);
        if (sql.contains(":from")) {
            nativeQuery.setParameter("from", from).setParameter("to", to);
        }
        List<?> raw = nativeQuery.getResultList();
        List<String> cols = columns(name);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            Object[] values = item instanceof Object[] arr ? arr : new Object[] {item};
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < values.length; i++) {
                row.put(i < cols.size() ? cols.get(i) : "value" + i, values[i]);
            }
            result.add(row);
        }
        return result;
    }

    private List<String> columns(String reportName) {
        return switch (reportName) {
            case "sales", "gstr1" ->
                List.of("date", "number", "customer", "subtotal", "discount", "cgst", "sgst", "igst", "grandTotal");
            case "purchase" ->
                List.of("date", "number", "supplier", "subtotal", "discount", "cgst", "sgst", "igst", "grandTotal");
            case "stock-ledger" ->
                List.of("date", "type", "product", "warehouse", "reference", "inward", "outward", "unitCost");
            case "stock-summary", "inventory-valuation" ->
                List.of("sku", "product", "warehouseCode", "warehouse", "quantity", "value");
            case "hsn", "product-sales" -> List.of("sku", "product", "hsn", "quantity", "discount", "amount");
            case "profit-summary" ->
                List.of(
                        "grossSales",
                        "salesDiscount",
                        "netSales",
                        "salesTotal",
                        "purchaseDiscount",
                        "purchases",
                        "grossProfit");
            case "outstanding-receivables" ->
                List.of("number", "date", "customer", "grandTotal", "amountPaid", "outstanding");
            case "outstanding-payables" ->
                List.of("number", "date", "supplier", "grandTotal", "amountPaid", "outstanding");
            case "customer-statement" ->
                List.of("customer", "number", "date", "grandTotal", "amountPaid", "outstanding");
            case "supplier-statement" ->
                List.of("supplier", "number", "date", "grandTotal", "amountPaid", "outstanding");
            default -> List.of("partyId", "number", "date", "grandTotal", "amountPaid", "outstanding");
        };
    }
}
