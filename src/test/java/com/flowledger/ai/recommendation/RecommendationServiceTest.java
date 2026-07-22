package com.flowledger.ai.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.entity.AiRecommendation;
import com.flowledger.ai.repository.AiRecommendationRepository;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.inventory.dto.InventoryDtos.Alert;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.payment.service.PaymentService;
import com.flowledger.sales.service.SalesInvoiceService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock
    AiRecommendationRepository repository;

    @Mock
    InventoryService inventoryService;

    @Mock
    PaymentService paymentService;

    @Mock
    SalesInvoiceService salesInvoiceService;

    private RecommendationService service;
    private RecommendationGenerator generator;
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(orgId, userId);
        service = new RecommendationService(repository);
        generator = new RecommendationGenerator(
                service, repository, inventoryService, paymentService, salesInvoiceService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createDefaultsToNewStatus() {
        when(repository.save(any(AiRecommendation.class))).thenAnswer(inv -> {
            AiRecommendation r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });

        AiDtos.RecommendationResponse resp = service.create(new AiDtos.RecommendationCreateRequest(
                RecommendationType.INVENTORY_RISK.name(),
                "HIGH",
                "Low stock",
                "desc",
                new BigDecimal("0.8"),
                "reason",
                null,
                "Reorder",
                "PRODUCT",
                UUID.randomUUID()));

        assertEquals("NEW", resp.status());
        ArgumentCaptor<AiRecommendation> captor = ArgumentCaptor.forClass(AiRecommendation.class);
        verify(repository).save(captor.capture());
        assertEquals("NEW", captor.getValue().getStatus());
    }

    @Test
    void acknowledgeAndDismiss() {
        UUID id = UUID.randomUUID();
        AiRecommendation existing = new AiRecommendation();
        existing.setId(id);
        existing.setOrganizationId(orgId);
        existing.setType(RecommendationType.GST_WARNING.name());
        existing.setPriority("MEDIUM");
        existing.setTitle("GST");
        existing.setStatus("NEW");
        when(repository.findByIdAndOrganizationId(id, orgId)).thenReturn(java.util.Optional.of(existing));
        when(repository.save(any(AiRecommendation.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("ACKNOWLEDGED", service.acknowledge(id).status());
        assertEquals("DISMISSED", service.dismiss(id).status());
    }

    @Test
    void generatorSeedsInventoryRiskFromLowStock() {
        UUID productId = UUID.randomUUID();
        when(inventoryService.lowStockAlerts(false))
                .thenReturn(List.of(new Alert(productId, "Widget", BigDecimal.ONE, BigDecimal.TEN)));
        when(repository.existsByOrganizationIdAndTypeAndRelatedEntityIdAndStatusIn(
                        eq(orgId), eq("INVENTORY_RISK"), eq(productId), anyList()))
                .thenReturn(false);
        when(paymentService.list()).thenReturn(List.of());
        when(salesInvoiceService.list(isNull(), isNull())).thenReturn(List.of());
        when(repository.save(any(AiRecommendation.class))).thenAnswer(inv -> {
            AiRecommendation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        int created = generator.generateHeuristics();
        assertTrue(created >= 1);
        verify(repository).save(any(AiRecommendation.class));
    }

    @Test
    void generatorSkipsWhenOpenRecommendationExists() {
        UUID productId = UUID.randomUUID();
        when(inventoryService.lowStockAlerts(false))
                .thenReturn(List.of(new Alert(productId, "Widget", BigDecimal.ONE, BigDecimal.TEN)));
        when(repository.existsByOrganizationIdAndTypeAndRelatedEntityIdAndStatusIn(
                        eq(orgId), eq("INVENTORY_RISK"), eq(productId), anyList()))
                .thenReturn(true);
        when(paymentService.list()).thenReturn(List.of());
        when(salesInvoiceService.list(isNull(), isNull())).thenReturn(List.of());

        int created = generator.generateHeuristics();
        assertEquals(0, created);
        verify(repository, never()).save(any());
    }
}
