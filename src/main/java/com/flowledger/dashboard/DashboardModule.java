package com.flowledger.dashboard;

import com.flowledger.common.tenant.TenantContext;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

@Service
class DashboardService {
    @PersistenceContext
    EntityManager em;

    Map<String, Object> summary() {
        UUID org = TenantContext.getOrganizationId();
        LocalDate today = LocalDate.now(), month = today.withDayOfMonth(1);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("todaySales", sum("sales_invoices", "invoice_date", today, today, org));
        r.put("monthSales", sum("sales_invoices", "invoice_date", month, today, org));
        r.put("todayPurchases", sum("purchase_invoices", "invoice_date", today, today, org));
        r.put("monthPurchases", sum("purchase_invoices", "invoice_date", month, today, org));
        r.put("receivables", outstanding("sales_invoices", org));
        r.put("payables", outstanding("purchase_invoices", org));
        r.put("overdueInvoices", count("sales_invoices", "due_date<current_date and outstanding_amount>0", org));
        r.put(
                "outOfStock",
                count(
                        "products",
                        "id not in (select product_id from inventory_transactions where organization_id='" + org
                                + "' group by product_id having sum(inward_qty-outward_qty)>0)",
                        org));
        r.put("lowStock", count("products", "minimum_stock_level>0", org));
        r.put("salesTrend", trend("sales_invoices", org));
        r.put("purchaseTrend", trend("purchase_invoices", org));
        r.put("topProducts", topProducts(org));
        r.put("topCustomers", topCustomers(org));
        return r;
    }

    private Object sum(String table, String date, LocalDate from, LocalDate to, UUID org) {
        return em.createNativeQuery("select coalesce(sum(grand_total),0) from " + table
                        + " where organization_id=:org and " + date + " between :from and :to")
                .setParameter("org", org)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
    }

    private Object outstanding(String table, UUID org) {
        return em.createNativeQuery(
                        "select coalesce(sum(outstanding_amount),0) from " + table + " where organization_id=:org")
                .setParameter("org", org)
                .getSingleResult();
    }

    private Object count(String table, String where, UUID org) {
        return em.createNativeQuery("select count(*) from " + table + " where organization_id=:org and " + where)
                .setParameter("org", org)
                .getSingleResult();
    }

    private List<?> trend(String table, UUID org) {
        return em.createNativeQuery(
                        "select to_char(invoice_date,'YYYY-MM'),sum(grand_total) from " + table
                                + " where organization_id=:org and invoice_date>=current_date-interval '6 months' group by 1 order by 1")
                .setParameter("org", org)
                .getResultList();
    }

    private List<?> topProducts(UUID org) {
        return em.createNativeQuery(
                        "select product_id,sum(line_total) amount from sales_invoice_items where sales_invoice_id in (select id from sales_invoices where organization_id=:org) group by product_id order by amount desc limit 5")
                .setParameter("org", org)
                .getResultList();
    }

    private List<?> topCustomers(UUID org) {
        return em.createNativeQuery(
                        "select customer_id,sum(grand_total) amount from sales_invoices where organization_id=:org group by customer_id order by amount desc limit 5")
                .setParameter("org", org)
                .getResultList();
    }
}

@RestController
@RequestMapping("/api/v1/dashboard")
class DashboardController {
    private final DashboardService service;

    DashboardController(DashboardService s) {
        service = s;
    }

    @GetMapping
    Map<String, Object> summary() {
        return service.summary();
    }
}
