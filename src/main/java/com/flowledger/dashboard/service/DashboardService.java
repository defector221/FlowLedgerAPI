package com.flowledger.dashboard.service;

import com.flowledger.common.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    @PersistenceContext
    private EntityManager em;

    public Map<String, Object> summary() {
        UUID org = TenantContext.getOrganizationId();
        LocalDate today = LocalDate.now();
        LocalDate month = today.withDayOfMonth(1);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("todaySales", sumConfirmed("sales_invoices", "invoice_date", today, today, org));
        r.put("monthSales", sumConfirmed("sales_invoices", "invoice_date", month, today, org));
        r.put("todayPurchases", sumConfirmed("purchase_invoices", "invoice_date", today, today, org));
        r.put("monthPurchases", sumConfirmed("purchase_invoices", "invoice_date", month, today, org));
        r.put("todaySalesDiscount", sumDiscount("sales_invoices", "invoice_date", today, today, org));
        r.put("monthSalesDiscount", sumDiscount("sales_invoices", "invoice_date", month, today, org));
        r.put("receivables", outstandingOpen("sales_invoices", org));
        r.put("payables", outstandingOpen("purchase_invoices", org));
        r.put(
                "overdueInvoices",
                count(
                        "sales_invoices",
                        "status not in ('DRAFT','CANCELLED') and due_date is not null "
                                + "and due_date < current_date and outstanding_amount > 0",
                        org));
        r.put(
                "outOfStock",
                count(
                        "products",
                        "active = true and coalesce(item_type, 'PRODUCT') = 'PRODUCT' "
                                + "and id not in (select product_id from inventory_transactions "
                                + "where organization_id='"
                                + org
                                + "' group by product_id having sum(inward_qty-outward_qty)>0)",
                        org));
        r.put("lowStock", countLowStock(org));
        r.put("salesTrend", trend("sales_invoices", org));
        r.put("purchaseTrend", trend("purchase_invoices", org));
        r.put("topProducts", topProducts(org));
        r.put("topCustomers", topCustomers(org));
        return r;
    }

    /** Posted invoice statuses that should count toward sales/purchase totals and trends. */
    private static String postedStatusFilter(String table) {
        if ("purchase_invoices".equals(table)) {
            return "status in ('CONFIRMED','PAID','PARTIALLY_PAID')";
        }
        // Sales invoices move CONFIRMED → PARTIALLY_PAID / PAID / OVERDUE after payment;
        // all of those are real revenue (drafts/cancelled are excluded).
        return "status in ('CONFIRMED','PARTIALLY_PAID','PAID','OVERDUE')";
    }

    private Object sumConfirmed(String table, String date, LocalDate from, LocalDate to, UUID org) {
        String statusFilter = postedStatusFilter(table);
        return em.createNativeQuery("select coalesce(sum(grand_total),0) from " + table
                        + " where organization_id=:org and " + statusFilter + " and " + date + " between :from and :to")
                .setParameter("org", org)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
    }

    private Object sumDiscount(String table, String date, LocalDate from, LocalDate to, UUID org) {
        String statusFilter = postedStatusFilter(table);
        return em.createNativeQuery("select coalesce(sum(discount_total),0) from " + table
                        + " where organization_id=:org and " + statusFilter + " and " + date + " between :from and :to")
                .setParameter("org", org)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
    }

    private Object outstandingOpen(String table, UUID org) {
        String statusFilter = table.equals("purchase_invoices")
                ? "status not in ('DRAFT','CANCELLED')"
                : "status not in ('DRAFT','CANCELLED')";
        return em.createNativeQuery("select coalesce(sum(outstanding_amount),0) from " + table
                        + " where organization_id=:org and " + statusFilter + " and outstanding_amount > 0")
                .setParameter("org", org)
                .getSingleResult();
    }

    private Object count(String table, String where, UUID org) {
        return em.createNativeQuery("select count(*) from " + table + " where organization_id=:org and " + where)
                .setParameter("org", org)
                .getSingleResult();
    }

    /** Products at or below their minimum stock level (stocked products only). */
    private Object countLowStock(UUID org) {
        return em.createNativeQuery(
                        """
                        select count(*) from products p
                        where p.organization_id = :org
                          and p.active = true
                          and coalesce(p.item_type, 'PRODUCT') = 'PRODUCT'
                          and p.minimum_stock_level > 0
                          and coalesce((
                                select sum(t.inward_qty - t.outward_qty)
                                from inventory_transactions t
                                where t.organization_id = p.organization_id
                                  and t.product_id = p.id
                          ), 0) <= p.minimum_stock_level
                        """)
                .setParameter("org", org)
                .getSingleResult();
    }

    private List<?> trend(String table, UUID org) {
        String statusFilter = postedStatusFilter(table);
        return em.createNativeQuery(
                        "select to_char(invoice_date,'YYYY-MM'),sum(grand_total) from " + table
                                + " where organization_id=:org and " + statusFilter
                                + " and invoice_date>=current_date-interval '6 months' group by 1 order by 1")
                .setParameter("org", org)
                .getResultList();
    }

    private List<?> topProducts(UUID org) {
        return em.createNativeQuery(
                        "select product_id,sum(line_total) amount from sales_invoice_items where sales_invoice_id in (select id from sales_invoices where organization_id=:org and "
                                + postedStatusFilter("sales_invoices")
                                + ") group by product_id order by amount desc limit 5")
                .setParameter("org", org)
                .getResultList();
    }

    private List<?> topCustomers(UUID org) {
        return em.createNativeQuery(
                        "select customer_id,sum(grand_total) amount from sales_invoices where organization_id=:org and "
                                + postedStatusFilter("sales_invoices")
                                + " group by customer_id order by amount desc limit 5")
                .setParameter("org", org)
                .getResultList();
    }
}
