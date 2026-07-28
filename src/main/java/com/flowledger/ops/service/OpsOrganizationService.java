package com.flowledger.ops.service;

import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.auth.repository.UserRepository;
import com.flowledger.ops.audit.OpsAuditService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.subscription.entity.OrganizationSubscription;
import com.flowledger.subscription.entity.SubscriptionPlan;
import com.flowledger.subscription.repository.OrganizationSubscriptionRepository;
import com.flowledger.subscription.repository.SubscriptionPlanRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OpsOrganizationService {
    private static final List<String> NON_CASCADE_ORG_TABLES = List.of(
            "ai_messages",
            "ai_audit_log",
            "ai_recommendations",
            "ai_embeddings",
            "ai_knowledge_documents",
            "ai_forecast_runs",
            "ai_agent_runs",
            "ai_workflow_drafts",
            "ai_conversations",
            "audit_logs");

    /**
     * Explicit deletes that must run before org CASCADE, ordered leaf → parent where possible.
     * Covers non-cascade FKs onto products / sibling documents that race with org CASCADE.
     */
    private static final List<String> PRE_ORG_DELETE_SQL = List.of(
            // Sales line items (product_id without ON DELETE CASCADE)
            """
            DELETE FROM quotation_items WHERE quotation_id IN
              (SELECT id FROM quotations WHERE organization_id = :id)
            """,
            """
            DELETE FROM sales_order_items WHERE sales_order_id IN
              (SELECT id FROM sales_orders WHERE organization_id = :id)
            """,
            """
            DELETE FROM delivery_challan_items WHERE delivery_challan_id IN
              (SELECT id FROM delivery_challans WHERE organization_id = :id)
            """,
            """
            DELETE FROM sales_invoice_items WHERE sales_invoice_id IN
              (SELECT id FROM sales_invoices WHERE organization_id = :id)
            """,
            """
            DELETE FROM sales_return_items WHERE sales_return_id IN
              (SELECT id FROM sales_returns WHERE organization_id = :id)
            """,
            // Purchase line items
            """
            DELETE FROM purchase_order_items WHERE purchase_order_id IN
              (SELECT id FROM purchase_orders WHERE organization_id = :id)
            """,
            """
            DELETE FROM goods_receipt_items WHERE goods_receipt_id IN
              (SELECT id FROM goods_receipts WHERE organization_id = :id)
            """,
            """
            DELETE FROM purchase_invoice_items WHERE purchase_invoice_id IN
              (SELECT id FROM purchase_invoices WHERE organization_id = :id)
            """,
            """
            DELETE FROM purchase_return_items WHERE purchase_return_id IN
              (SELECT id FROM purchase_returns WHERE organization_id = :id)
            """,
            // Documents that FK sibling headers without CASCADE
            "DELETE FROM debit_notes WHERE organization_id = :id",
            "DELETE FROM credit_notes WHERE organization_id = :id",
            "DELETE FROM purchase_returns WHERE organization_id = :id",
            "DELETE FROM sales_returns WHERE organization_id = :id",
            // Inventory / catalog rows that FK products without CASCADE
            "DELETE FROM inventory_cost_layers WHERE organization_id = :id",
            "DELETE FROM stock_reservations WHERE organization_id = :id",
            "DELETE FROM inventory_transactions WHERE organization_id = :id",
            "DELETE FROM inventory_batches WHERE organization_id = :id",
            "DELETE FROM serial_numbers WHERE organization_id = :id",
            "DELETE FROM supplier_catalog_items WHERE organization_id = :id",
            "UPDATE scan_history SET resolved_product_id = NULL WHERE organization_id = :id",
            """
            UPDATE shipment_lines SET product_id = NULL
             WHERE product_id IN (SELECT id FROM products WHERE organization_id = :id)
            """,
            // Drop products now so org CASCADE cannot race with leftover line-item FKs
            "DELETE FROM products WHERE organization_id = :id");

    private final OrganizationRepository organizations;
    private final OrganizationSettingsRepository settings;
    private final OrganizationSubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final OpsAuditService audit;
    private final UserRepository users;
    private final OrganizationMembershipRepository memberships;

    @PersistenceContext
    private EntityManager em;

    public OpsOrganizationService(
            OrganizationRepository organizations,
            OrganizationSettingsRepository settings,
            OrganizationSubscriptionRepository subscriptions,
            SubscriptionPlanRepository plans,
            OpsAuditService audit,
            UserRepository users,
            OrganizationMembershipRepository memberships) {
        this.organizations = organizations;
        this.settings = settings;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.audit = audit;
        this.users = users;
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> list(String q, Pageable pageable) {
        return organizations.search(q == null ? "" : q.trim(), pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(UUID id) {
        Organization org = organizations.findById(id).orElseThrow(() -> notFound());
        Map<String, Object> m = toSummary(org);
        m.put("legalName", org.getLegalName());
        m.put("gstin", org.getGstin());
        m.put("pan", org.getPan());
        m.put("phone", org.getPhone());
        m.put("website", org.getWebsite());
        m.put("billingAddress", org.getBillingAddress());
        m.put("shippingAddress", org.getShippingAddress());
        m.put("city", org.getCity());
        m.put("state", org.getState());
        m.put("stateCode", org.getStateCode());
        m.put("financialYearStart", org.getFinancialYearStart());
        m.put("editionCode", org.getEditionCode());
        m.put("onboardingCompleted", org.isOnboardingCompleted());
        m.put("onboardingCompletedAt", org.getOnboardingCompletedAt());
        m.put("inventoryCostingMethod", org.getInventoryCostingMethod());
        m.put("updatedAt", org.getUpdatedAt());

        List<Map<String, Object>> members = loadMembers(id);
        m.put("users", members);
        m.put("adminUsers", members.stream()
                .filter(u -> Boolean.TRUE.equals(u.get("admin")))
                .toList());
        members.stream()
                .filter(u -> Boolean.TRUE.equals(u.get("admin")))
                .findFirst()
                .ifPresentOrElse(
                        admin -> {
                            m.put("primaryAdminEmail", admin.get("email"));
                            m.put("primaryContactName", admin.get("fullName"));
                            m.put("primaryContactPhone", admin.get("phone"));
                        },
                        () -> {
                            m.put("primaryAdminEmail", org.getEmail());
                            m.put("primaryContactName", null);
                            m.put("primaryContactPhone", org.getPhone());
                        });

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("users", members.size());
        counts.put("branches", countTable("branches", id));
        counts.put("warehouses", countTable("warehouses", id));
        counts.put("stores", countTable("retail_stores", id));
        counts.put("products", countTable("products", id));
        counts.put("customers", countTable("customers", id));
        counts.put("suppliers", countTable("suppliers", id));
        m.put("counts", counts);
        return m;
    }

    private List<Map<String, Object>> loadMembers(UUID orgId) {
        List<OrganizationMembership> orgMemberships = memberships.findByOrganizationId(orgId);
        Map<UUID, OrganizationMembership> byUser = orgMemberships.stream()
                .collect(Collectors.toMap(OrganizationMembership::getUserId, x -> x, (a, b) -> a));

        List<User> orgUsers = new ArrayList<>(users.findByOrganizationId(orgId));
        for (UUID userId : byUser.keySet()) {
            if (orgUsers.stream().noneMatch(u -> u.getId().equals(userId))) {
                users.findById(userId).ifPresent(orgUsers::add);
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (User user : orgUsers) {
            OrganizationMembership membership = byUser.get(user.getId());
            List<String> roleCodes = new ArrayList<>();
            if (membership != null && membership.getRoles() != null) {
                roleCodes.addAll(membership.getRoles().stream().map(Role::getCode).sorted().toList());
            }
            if (roleCodes.isEmpty() && user.getRoles() != null) {
                roleCodes.addAll(user.getRoles().stream().map(Role::getCode).sorted().toList());
            }
            boolean admin = roleCodes.stream()
                    .anyMatch(c -> "ORGANIZATION_ADMIN".equalsIgnoreCase(c) || "ORG_ADMIN".equalsIgnoreCase(c));
            String fullName = ((user.getFirstName() == null ? "" : user.getFirstName())
                            + " "
                            + (user.getLastName() == null ? "" : user.getLastName()))
                    .trim();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("email", user.getEmail());
            row.put("firstName", user.getFirstName());
            row.put("lastName", user.getLastName());
            row.put("fullName", fullName.isBlank() ? user.getEmail() : fullName);
            row.put("phone", user.getPhone());
            row.put("active", user.isActive());
            row.put("status", membership != null ? membership.getStatus() : user.getUserStatus());
            row.put("roles", roleCodes);
            row.put("admin", admin);
            out.add(row);
        }
        out.sort(Comparator.comparing((Map<String, Object> r) -> !Boolean.TRUE.equals(r.get("admin")))
                .thenComparing(r -> String.valueOf(r.get("email"))));
        return out;
    }

    private long countTable(String table, UUID orgId) {
        if (!tableExists(table)) {
            return 0;
        }
        Number n = (Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE organization_id = :id")
                .setParameter("id", orgId)
                .getSingleResult();
        return n == null ? 0 : n.longValue();
    }

    @Transactional
    public Map<String, Object> create(CreateOrgRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        Organization org = new Organization();
        org.setName(req.name().trim());
        org.setCountry(req.country() != null ? req.country() : "India");
        org.setCurrency(req.currency() != null ? req.currency() : "INR");
        org.setEmail(req.email());
        org.setFinancialYearStart(req.fiscalYearStart() != null ? req.fiscalYearStart() : "04-01");
        org.setLifecycleStatus("ACTIVE");
        org.setActive(true);
        org.setOnboardingCompleted(false);
        org = organizations.save(org);
        OrganizationSettings s = new OrganizationSettings();
        s.setOrganizationId(org.getId());
        settings.save(s);
        if (req.planCode() != null && !req.planCode().isBlank()) {
            plans.findAll().stream()
                    .filter(p -> req.planCode().equalsIgnoreCase(p.getCode()))
                    .findFirst()
                    .ifPresent(plan -> {
                        // subscription attach is best-effort; ActivationService may own full flow
                    });
        }
        audit.record("ORG_CREATE", org.getId(), "Organization", org.getId().toString(), "SUCCESS");
        return toSummary(org);
    }

    @Transactional
    public Map<String, Object> setStatus(UUID id, String status) {
        Organization org = organizations.findById(id).orElseThrow(() -> notFound());
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("ACTIVE", "SUSPENDED", "ARCHIVED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACTIVE, SUSPENDED, or ARCHIVED");
        }
        org.setLifecycleStatus(normalized);
        org.setActive("ACTIVE".equals(normalized));
        organizations.save(org);
        audit.record("ORG_STATUS_" + normalized, id, "Organization", id.toString(), "SUCCESS");
        return toSummary(org);
    }

    /**
     * Hard-delete an organization and cascaded tenant data. Requires confirmName to match the org name.
     * Clears known non-cascade FK blockers first (settings→warehouse cycle, users, AI/audit tables).
     */
    @Transactional
    public Map<String, Object> purge(UUID id, String confirmName) {
        Organization org = organizations.findById(id).orElseThrow(() -> notFound());
        if (confirmName == null || !confirmName.trim().equals(org.getName())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "confirmName must exactly match the organization name");
        }

        String name = org.getName();

        // Break settings ↔ warehouse cycle before CASCADE delete of warehouses.
        em.createNativeQuery(
                        "UPDATE organization_settings SET default_warehouse_id = NULL WHERE organization_id = :id")
                .setParameter("id", id)
                .executeUpdate();
        em.createNativeQuery(
                        "UPDATE users SET last_active_organization_id = NULL WHERE last_active_organization_id = :id")
                .setParameter("id", id)
                .executeUpdate();

        for (String table : NON_CASCADE_ORG_TABLES) {
            if (tableExists(table)) {
                em.createNativeQuery("DELETE FROM " + table + " WHERE organization_id = :id")
                        .setParameter("id", id)
                        .executeUpdate();
            }
        }

        // Clear product / document FK blockers before Hibernate CASCADE deletes the org.
        clearProductForeignKeys(id);
        for (String sql : PRE_ORG_DELETE_SQL) {
            executePreDelete(sql, id);
        }

        // Tenant users whose only membership is this org (memberships cascade from users).
        @SuppressWarnings("unchecked")
        List<Object> rawUserIds = em.createNativeQuery(
                        """
                        SELECT DISTINCT u.id
                        FROM users u
                        WHERE (
                            u.organization_id = :id
                            OR EXISTS (
                                SELECT 1 FROM organization_memberships m
                                WHERE m.user_id = u.id AND m.organization_id = :id
                            )
                        )
                        AND NOT EXISTS (
                            SELECT 1 FROM organization_memberships m2
                            WHERE m2.user_id = u.id AND m2.organization_id <> :id
                        )
                        """)
                .setParameter("id", id)
                .getResultList();
        List<UUID> userIds = rawUserIds.stream()
                .map(row -> row instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(row)))
                .toList();

        if (!userIds.isEmpty()) {
            if (tableExists("audit_logs")) {
                em.createNativeQuery("UPDATE audit_logs SET user_id = NULL WHERE user_id IN (:ids)")
                        .setParameter("ids", userIds)
                        .executeUpdate();
            }
            em.createNativeQuery("DELETE FROM users WHERE id IN (:ids)")
                    .setParameter("ids", userIds)
                    .executeUpdate();
        }

        // Multi-org users: drop home-org pointer so org delete is not blocked.
        em.createNativeQuery("UPDATE users SET organization_id = NULL WHERE organization_id = :id")
                .setParameter("id", id)
                .executeUpdate();

        organizations.delete(org);
        em.flush();

        audit.record("ORG_PURGE", id, "Organization", id.toString(), "SUCCESS");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", name);
        result.put("deleted", true);
        return result;
    }

    private boolean tableExists(String table) {
        Number count = (Number) em.createNativeQuery(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_name = :table
                        """)
                .setParameter("table", table)
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }

    private void executePreDelete(String sql, UUID orgId) {
        String lower = sql.toLowerCase();
        // Skip statements whose primary target table is missing (optional modules / older DBs).
        String target = extractPrimaryTable(lower);
        if (target != null && !tableExists(target)) {
            return;
        }
        try {
            em.createNativeQuery(sql).setParameter("id", orgId).executeUpdate();
        } catch (RuntimeException ex) {
            // Optional column / table shape differences should not block purge of core data.
            if (target != null && (target.startsWith("shipment") || "scan_history".equals(target))) {
                return;
            }
            throw ex;
        }
    }

    private static String extractPrimaryTable(String lowerSql) {
        for (String prefix : List.of("delete from ", "update ")) {
            int idx = lowerSql.indexOf(prefix);
            if (idx >= 0) {
                String rest = lowerSql.substring(idx + prefix.length()).trim();
                int end = 0;
                while (end < rest.length() && (Character.isLetterOrDigit(rest.charAt(end)) || rest.charAt(end) == '_')) {
                    end++;
                }
                return end > 0 ? rest.substring(0, end) : null;
            }
        }
        return null;
    }

    /**
     * Deletes every row that formally FKs into this org's products (covers tables added later).
     * Runs several passes in case child tables FK each other.
     */
    @SuppressWarnings("unchecked")
    private void clearProductForeignKeys(UUID orgId) {
        List<Object[]> fks = em.createNativeQuery(
                        """
                        SELECT DISTINCT kcu.table_name, kcu.column_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_name = kcu.constraint_name
                         AND tc.table_schema = kcu.table_schema
                        JOIN information_schema.constraint_column_usage ccu
                          ON ccu.constraint_name = tc.constraint_name
                         AND ccu.table_schema = tc.table_schema
                        WHERE tc.constraint_type = 'FOREIGN KEY'
                          AND tc.table_schema = 'public'
                          AND ccu.table_name = 'products'
                          AND ccu.column_name = 'id'
                        """)
                .getResultList();

        for (int pass = 0; pass < 3; pass++) {
            int deleted = 0;
            for (Object[] fk : fks) {
                String table = String.valueOf(fk[0]);
                String column = String.valueOf(fk[1]);
                if (!isSafeIdentifier(table) || !isSafeIdentifier(column) || "products".equals(table)) {
                    continue;
                }
                deleted += em.createNativeQuery(
                                "DELETE FROM " + table + " WHERE " + column
                                        + " IN (SELECT id FROM products WHERE organization_id = :id)")
                        .setParameter("id", orgId)
                        .executeUpdate();
            }
            if (deleted == 0) {
                break;
            }
        }
    }

    private static boolean isSafeIdentifier(String name) {
        return name != null && name.matches("[a-z_][a-z0-9_]*");
    }

    private Map<String, Object> toSummary(Organization org) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", org.getId());
        m.put("name", org.getName());
        m.put("email", org.getEmail());
        m.put("country", org.getCountry());
        m.put("currency", org.getCurrency());
        m.put("lifecycleStatus", org.getLifecycleStatus());
        m.put("active", org.isActive());
        m.put("createdAt", org.getCreatedAt());
        subscriptions.findByOrganizationId(org.getId()).ifPresentOrElse(
                sub -> {
                    m.put("subscriptionStatus", sub.getStatus());
                    m.put("billingCycle", sub.getBillingCycle());
                    SubscriptionPlan plan = sub.getPlan();
                    if (plan != null) {
                        m.put("planCode", plan.getCode());
                        m.put("planName", plan.getName());
                    }
                },
                () -> {
                    m.put("subscriptionStatus", null);
                    m.put("planCode", null);
                });
        return m;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
    }

    public record CreateOrgRequest(
            String name,
            String email,
            String country,
            String currency,
            String fiscalYearStart,
            String planCode,
            String demoScenario) {}
}
