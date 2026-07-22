package com.flowledger.ai.recommendation;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.repository.AiRecommendationRepository;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.inventory.dto.InventoryDtos.Alert;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.payment.entity.Payment;
import com.flowledger.payment.service.PaymentService;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.service.SalesInvoiceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Heuristic recommendation seeder. Callable from AFTER_COMMIT event bridges — never from inside
 * an ERP posting transaction.
 */
@Service
@ConditionalOnAiEnabled
public class RecommendationGenerator {
    private static final Logger log = LoggerFactory.getLogger(RecommendationGenerator.class);
    private static final List<String> OPEN_STATUSES = List.of("NEW", "OPEN");

    private final RecommendationService recommendationService;
    private final AiRecommendationRepository repository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final SalesInvoiceService salesInvoiceService;

    public RecommendationGenerator(
            RecommendationService recommendationService,
            AiRecommendationRepository repository,
            InventoryService inventoryService,
            PaymentService paymentService,
            SalesInvoiceService salesInvoiceService) {
        this.recommendationService = recommendationService;
        this.repository = repository;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.salesInvoiceService = salesInvoiceService;
    }

    /** Run inventory / cash / credit heuristics for the current tenant. */
    @Transactional
    public int generateHeuristics() {
        UUID org = TenantContext.getOrganizationId();
        int created = 0;
        created += seedInventoryRisks(org);
        created += seedDuplicatePayments(org);
        created += seedCustomerCreditRisks(org);
        created += seedCashFlowRisk(org);
        return created;
    }

    /** After a product index upsert — check low-stock for that product (or all if null). */
    @Transactional
    public int onProductChanged(UUID productId) {
        UUID org = TenantContext.getOrganizationId();
        return seedInventoryRisks(org, productId);
    }

    /** After a customer index upsert — outstanding credit check. */
    @Transactional
    public int onCustomerChanged(UUID customerId) {
        UUID org = TenantContext.getOrganizationId();
        return seedCustomerCreditRisks(org, customerId);
    }

    private int seedInventoryRisks(UUID org) {
        return seedInventoryRisks(org, null);
    }

    private int seedInventoryRisks(UUID org, UUID productId) {
        int created = 0;
        try {
            List<Alert> low = inventoryService.lowStockAlerts(false);
            for (Alert alert : low) {
                if (productId != null && !productId.equals(alert.productId())) {
                    continue;
                }
                if (hasOpen(org, RecommendationType.INVENTORY_RISK, alert.productId())) {
                    continue;
                }
                Map<String, Object> evidence = new HashMap<>();
                evidence.put("productId", alert.productId().toString());
                evidence.put("productName", alert.productName());
                evidence.put("available", alert.available());
                evidence.put("threshold", alert.threshold());
                recommendationService.create(new AiDtos.RecommendationCreateRequest(
                        RecommendationType.INVENTORY_RISK.name(),
                        "HIGH",
                        "Low stock: " + alert.productName(),
                        "Available quantity is at or below the minimum stock level.",
                        bd("0.85"),
                        "Stock " + alert.available() + " <= minimum " + alert.threshold(),
                        evidence,
                        "Create a purchase order or transfer stock from another warehouse.",
                        "PRODUCT",
                        alert.productId()));
                created++;
            }
        } catch (Exception e) {
            log.warn("Inventory risk heuristics failed: {}", e.getMessage());
        }
        return created;
    }

    private int seedDuplicatePayments(UUID org) {
        int created = 0;
        try {
            List<Payment> payments = paymentService.list();
            Map<String, List<Payment>> groups = payments.stream()
                    .filter(p -> p.getAmount() != null && p.getPaymentDate() != null)
                    .collect(Collectors.groupingBy(
                            p -> p.getAmount().setScale(2, RoundingMode.HALF_UP) + "|" + p.getPaymentDate() + "|"
                                    + String.valueOf(p.getPaymentType())));
            for (Map.Entry<String, List<Payment>> entry : groups.entrySet()) {
                if (entry.getValue().size() < 2) {
                    continue;
                }
                Payment first = entry.getValue().get(0);
                if (hasOpen(org, RecommendationType.DUPLICATE_PAYMENT, first.getId())) {
                    continue;
                }
                Map<String, Object> evidence = new HashMap<>();
                evidence.put(
                        "paymentIds",
                        entry.getValue().stream().map(p -> p.getId().toString()).toList());
                evidence.put("amount", first.getAmount());
                evidence.put("date", first.getPaymentDate().toString());
                evidence.put("count", entry.getValue().size());
                recommendationService.create(new AiDtos.RecommendationCreateRequest(
                        RecommendationType.DUPLICATE_PAYMENT.name(),
                        "HIGH",
                        "Possible duplicate payments",
                        "Multiple payments share the same amount, date, and type.",
                        bd("0.70"),
                        entry.getValue().size() + " payments match amount/date/type",
                        evidence,
                        "Review and void or reallocate any accidental duplicates.",
                        "PAYMENT",
                        first.getId()));
                created++;
            }
        } catch (Exception e) {
            log.warn("Duplicate payment heuristics failed: {}", e.getMessage());
        }
        return created;
    }

    private int seedCustomerCreditRisks(UUID org) {
        return seedCustomerCreditRisks(org, null);
    }

    private int seedCustomerCreditRisks(UUID org, UUID customerId) {
        int created = 0;
        try {
            List<SalesInvoice> invoices = salesInvoiceService.list(null, null);
            Map<UUID, BigDecimal> outstandingByCustomer = invoices.stream()
                    .filter(i -> i.getOutstandingAmount() != null
                            && i.getOutstandingAmount().signum() > 0
                            && i.getCustomerId() != null)
                    .filter(i -> customerId == null || customerId.equals(i.getCustomerId()))
                    .collect(Collectors.groupingBy(
                            SalesInvoice::getCustomerId,
                            Collectors.mapping(
                                    SalesInvoice::getOutstandingAmount,
                                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
            BigDecimal threshold = new BigDecimal("100000");
            for (Map.Entry<UUID, BigDecimal> entry : outstandingByCustomer.entrySet()) {
                if (entry.getValue().compareTo(threshold) < 0) {
                    continue;
                }
                if (hasOpen(org, RecommendationType.CUSTOMER_CREDIT_RISK, entry.getKey())) {
                    continue;
                }
                Map<String, Object> evidence = new HashMap<>();
                evidence.put("customerId", entry.getKey().toString());
                evidence.put("outstandingTotal", entry.getValue());
                evidence.put("threshold", threshold);
                recommendationService.create(new AiDtos.RecommendationCreateRequest(
                        RecommendationType.CUSTOMER_CREDIT_RISK.name(),
                        "MEDIUM",
                        "High customer outstanding",
                        "Customer outstanding receivable exceeds advisory credit threshold.",
                        bd("0.65"),
                        "Outstanding " + entry.getValue() + " >= " + threshold,
                        evidence,
                        "Follow up for collection or tighten credit terms.",
                        "CUSTOMER",
                        entry.getKey()));
                created++;
            }
        } catch (Exception e) {
            log.warn("Customer credit heuristics failed: {}", e.getMessage());
        }
        return created;
    }

    private int seedCashFlowRisk(UUID org) {
        try {
            List<SalesInvoice> invoices = salesInvoiceService.list(null, null);
            BigDecimal totalOutstanding = invoices.stream()
                    .map(SalesInvoice::getOutstandingAmount)
                    .filter(a -> a != null && a.signum() > 0)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalOutstanding.compareTo(new BigDecimal("250000")) < 0) {
                return 0;
            }
            if (hasOpen(org, RecommendationType.CASH_FLOW_RISK, null)) {
                return 0;
            }
            Map<String, Object> evidence = Map.of("totalOutstanding", totalOutstanding);
            recommendationService.create(new AiDtos.RecommendationCreateRequest(
                    RecommendationType.CASH_FLOW_RISK.name(),
                    "MEDIUM",
                    "Elevated receivables outstanding",
                    "Aggregate AR outstanding is high relative to the advisory cash-flow threshold.",
                    bd("0.60"),
                    "Total outstanding " + totalOutstanding,
                    evidence,
                    "Prioritize collections and review payment terms.",
                    null,
                    null));
            return 1;
        } catch (Exception e) {
            log.warn("Cash-flow heuristics failed: {}", e.getMessage());
            return 0;
        }
    }

    private boolean hasOpen(UUID org, RecommendationType type, UUID relatedEntityId) {
        if (relatedEntityId == null) {
            return repository.existsByOrganizationIdAndTypeAndStatusInAndRelatedEntityIdIsNull(
                    org, type.name(), OPEN_STATUSES);
        }
        return repository.existsByOrganizationIdAndTypeAndRelatedEntityIdAndStatusIn(
                org, type.name(), relatedEntityId, OPEN_STATUSES);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
