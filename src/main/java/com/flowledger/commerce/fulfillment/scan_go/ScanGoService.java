package com.flowledger.commerce.fulfillment.scan_go;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.auth.CommerceSecurityContext;
import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.checkout.repository.CommerceCheckoutSessionRepository;
import com.flowledger.commerce.customer.bridge.CommerceCustomerBridgeService;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.fulfillment.engine.FulfillmentOrchestrator;
import com.flowledger.commerce.fulfillment.order.entity.FulfillmentOrder;
import com.flowledger.commerce.fulfillment.scan_go.domain.ScanSessionStatus;
import com.flowledger.commerce.fulfillment.scan_go.entity.ScanExitToken;
import com.flowledger.commerce.fulfillment.scan_go.entity.ScanSession;
import com.flowledger.commerce.fulfillment.scan_go.entity.ScanSessionItem;
import com.flowledger.commerce.fulfillment.scan_go.repository.ScanExitTokenRepository;
import com.flowledger.commerce.fulfillment.scan_go.repository.ScanSessionItemRepository;
import com.flowledger.commerce.fulfillment.scan_go.repository.ScanSessionRepository;
import com.flowledger.commerce.fulfillment.verification.QrTokenService;
import com.flowledger.commerce.order.domain.CommerceOrderStatus;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import com.flowledger.commerce.order.repository.CommerceOrderLineRepository;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceStoreIndex;
import com.flowledger.commerce.publisher.repository.MarketplaceProductIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceStoreIndexRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.security.SecurityUtils;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ScanGoService {
    private final ScanSessionRepository sessions;
    private final ScanSessionItemRepository items;
    private final ScanExitTokenRepository exitTokens;
    private final MarketplaceProductIndexRepository productIndex;
    private final MarketplaceStoreIndexRepository storeIndex;
    private final CommerceOrderRepository orders;
    private final CommerceOrderLineRepository orderLines;
    private final CommerceCheckoutSessionRepository checkoutSessions;
    private final CommerceCustomerBridgeService customerBridge;
    private final FulfillmentOrchestrator fulfillmentOrchestrator;
    private final QrTokenService qrTokens;
    private final ObjectMapper objectMapper;

    public ScanGoService(
            ScanSessionRepository sessions,
            ScanSessionItemRepository items,
            ScanExitTokenRepository exitTokens,
            MarketplaceProductIndexRepository productIndex,
            MarketplaceStoreIndexRepository storeIndex,
            CommerceOrderRepository orders,
            CommerceOrderLineRepository orderLines,
            CommerceCheckoutSessionRepository checkoutSessions,
            CommerceCustomerBridgeService customerBridge,
            FulfillmentOrchestrator fulfillmentOrchestrator,
            QrTokenService qrTokens,
            ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.items = items;
        this.exitTokens = exitTokens;
        this.productIndex = productIndex;
        this.storeIndex = storeIndex;
        this.orders = orders;
        this.orderLines = orderLines;
        this.checkoutSessions = checkoutSessions;
        this.customerBridge = customerBridge;
        this.fulfillmentOrchestrator = fulfillmentOrchestrator;
        this.qrTokens = qrTokens;
        this.objectMapper = objectMapper;
    }

    public CommerceDtos.ScanSessionResponse openSession(CommerceDtos.OpenScanSessionRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        sessions.findByCustomerIdAndStoreIdAndStatus(customerId, request.storeId(), ScanSessionStatus.ACTIVE)
                .ifPresent(s -> {
                    throw new BusinessException("Active scan session already exists");
                });
        MarketplaceStoreIndex store = storeIndex
                .findByStoreIdAndPublishedTrue(request.storeId())
                .orElseThrow(() -> new ResourceNotFoundException("Store not published for commerce"));
        ScanSession session = new ScanSession();
        session.setCustomerId(customerId);
        session.setOrganizationId(store.getOrganizationId());
        session.setStoreId(request.storeId());
        session.setStatus(ScanSessionStatus.ACTIVE);
        session = sessions.save(session);
        return toResponse(session, List.of());
    }

    public CommerceDtos.ScanSessionResponse scanItem(UUID sessionId, CommerceDtos.ScanItemRequest request) {
        ScanSession session = requireActiveSession(sessionId);
        MarketplaceProductIndex index = productIndex
                .findFirstByStoreIdAndBarcodeAndPublishedTrue(session.getStoreId(), request.barcode())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found for barcode"));
        BigDecimal qty = request.quantity() != null ? request.quantity() : BigDecimal.ONE;
        ScanSessionItem line = new ScanSessionItem();
        line.setSessionId(sessionId);
        line.setProductId(index.getProductId());
        line.setQuantity(qty);
        line.setLineSubtotal(index.getPrice().multiply(qty));
        line.setLineTax(BigDecimal.ZERO);
        line.setLineTotal(line.getLineSubtotal());
        try {
            line.setProductSnapshot(objectMapper.writeValueAsString(Map.of(
                    "name", index.getName() != null ? index.getName() : "",
                    "sku", index.getSku() != null ? index.getSku() : "",
                    "barcode", index.getBarcode() != null ? index.getBarcode() : "")));
            line.setPriceSnapshot(objectMapper.writeValueAsString(Map.of("price", index.getPrice())));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        items.save(line);
        recalc(session);
        return toResponse(session, items.findBySessionIdOrderByCreatedAtAsc(sessionId));
    }

    public CommerceDtos.ScanSessionResponse markPaid(UUID sessionId) {
        ScanSession session = requireActiveSession(sessionId);
        if (session.getItemCount() == 0) {
            throw new BusinessException("Cannot pay for empty session");
        }
        session.setStatus(ScanSessionStatus.PAID);
        session.setPaidAt(OffsetDateTime.now());
        sessions.save(session);
        return toResponse(session, items.findBySessionIdOrderByCreatedAtAsc(sessionId));
    }

    public CommerceDtos.ScanExitResponse generateExitToken(UUID sessionId) {
        ScanSession session = sessions.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (session.getStatus() != ScanSessionStatus.PAID) {
            throw new BusinessException("Session must be paid before exit");
        }
        String raw = qrTokens.issueExitToken(sessionId);
        ScanExitToken token = new ScanExitToken();
        token.setSessionId(sessionId);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(OffsetDateTime.now().plusHours(2));
        exitTokens.save(token);
        return new CommerceDtos.ScanExitResponse(sessionId, raw);
    }

    public CommerceDtos.CommerceOrderResponse verifyExit(String rawToken) {
        UUID actorId = SecurityUtils.currentUserId();
        ScanExitToken token = exitTokens
                .findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessException("Invalid exit token"));
        if (token.getVerifiedAt() != null) {
            throw new BusinessException("Exit token already used");
        }
        ScanSession session = sessions.findById(token.getSessionId()).orElseThrow();
        token.setVerifiedAt(OffsetDateTime.now());
        token.setVerifiedBy(actorId);
        exitTokens.save(token);

        CommerceOrder order = createOrderFromSession(session);
        FulfillmentOrder fulfillment = fulfillmentOrchestrator.createFromOrder(order, session.getCustomerId());
        fulfillmentOrchestrator.fulfillScanAndGoExit(fulfillment.getId(), actorId);

        session.setStatus(ScanSessionStatus.COMPLETED);
        session.setCompletedAt(OffsetDateTime.now());
        session.setCommerceOrderId(order.getId());
        sessions.save(session);

        return new CommerceDtos.CommerceOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStoreId(),
                order.getStatus().name(),
                order.getFulfillmentType().name(),
                order.getCurrency(),
                order.getSubtotal(),
                order.getDiscountTotal(),
                order.getTaxTotal(),
                order.getShippingTotal(),
                order.getGrandTotal(),
                order.getConfirmedAt(),
                order.getConfirmedAt(),
                List.of());
    }

    public List<ScanSession> listActiveSessions(UUID storeId) {
        return sessions.findByStoreIdAndStatusIn(
                storeId, List.of(ScanSessionStatus.ACTIVE, ScanSessionStatus.PAID));
    }

    private CommerceOrder createOrderFromSession(ScanSession session) {
        UUID erpCustomerId = customerBridge.findOrCreateErpCustomer(session.getOrganizationId(), session.getCustomerId());
        CommerceCheckoutSession checkout = new CommerceCheckoutSession();
        checkout.setCustomerId(session.getCustomerId());
        checkout.setOrganizationId(session.getOrganizationId());
        checkout.setStoreId(session.getStoreId());
        checkout.setFulfillmentType(FulfillmentType.SCAN_AND_GO);
        checkout.setCurrency(session.getCurrency());
        checkout.setSubtotal(session.getSubtotal());
        checkout.setDiscountTotal(BigDecimal.ZERO);
        checkout.setTaxTotal(session.getTaxTotal());
        checkout.setShippingTotal(BigDecimal.ZERO);
        checkout.setGrandTotal(session.getGrandTotal());
        checkout = checkoutSessions.save(checkout);

        CommerceOrder order = new CommerceOrder();
        order.setCheckoutSessionId(checkout.getId());
        order.setCustomerId(session.getCustomerId());
        order.setOrganizationId(session.getOrganizationId());
        order.setStoreId(session.getStoreId());
        order.setErpCustomerId(erpCustomerId);
        order.setFulfillmentType(FulfillmentType.SCAN_AND_GO);
        order.setCurrency(session.getCurrency());
        order.setSubtotal(session.getSubtotal());
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setTaxTotal(session.getTaxTotal());
        order.setShippingTotal(BigDecimal.ZERO);
        order.setGrandTotal(session.getGrandTotal());
        order.setOrderNumber("SG-" + session.getId().toString().substring(0, 8).toUpperCase());
        order.setStatus(CommerceOrderStatus.PLACED);
        order.setConfirmedAt(OffsetDateTime.now());
        order = orders.save(order);

        for (ScanSessionItem item : items.findBySessionIdOrderByCreatedAtAsc(session.getId())) {
            CommerceOrderLine line = new CommerceOrderLine();
            line.setOrderId(order.getId());
            line.setProductId(item.getProductId());
            line.setQuantity(item.getQuantity());
            line.setLineSubtotal(item.getLineSubtotal());
            line.setLineTax(item.getLineTax());
            line.setLineTotal(item.getLineTotal());
            line.setProductSnapshot(item.getProductSnapshot());
            line.setPriceSnapshot(item.getPriceSnapshot());
            line.setTaxSnapshot("{}");
            line.setPromotionSnapshot("{}");
            orderLines.save(line);
        }
        return order;
    }

    private ScanSession requireActiveSession(UUID sessionId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        ScanSession session = sessions.findByIdAndCustomerId(sessionId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (session.getStatus() != ScanSessionStatus.ACTIVE) {
            throw new BusinessException("Scan session is not active");
        }
        return session;
    }

    private void recalc(ScanSession session) {
        List<ScanSessionItem> lines = items.findBySessionIdOrderByCreatedAtAsc(session.getId());
        session.setItemCount(lines.size());
        session.setSubtotal(lines.stream().map(ScanSessionItem::getLineSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        session.setTaxTotal(lines.stream().map(ScanSessionItem::getLineTax).reduce(BigDecimal.ZERO, BigDecimal::add));
        session.setGrandTotal(lines.stream().map(ScanSessionItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        sessions.save(session);
    }

    private CommerceDtos.ScanSessionResponse toResponse(ScanSession session, List<ScanSessionItem> lines) {
        return new CommerceDtos.ScanSessionResponse(
                session.getId(),
                session.getStoreId(),
                session.getStatus().name(),
                session.getCurrency(),
                session.getSubtotal(),
                session.getTaxTotal(),
                session.getGrandTotal(),
                session.getItemCount(),
                lines.stream()
                        .map(item -> new CommerceDtos.ScanSessionItemResponse(
                                item.getId(),
                                item.getProductId(),
                                item.getQuantity(),
                                item.getLineTotal(),
                                extractName(item.getProductSnapshot()),
                                extractBarcode(item.getProductSnapshot())))
                        .toList());
    }

    private static String extractName(String json) {
        return json.contains("\"name\"") ? json.replaceAll(".*\"name\"\\s*:\\s*\"([^\"]+)\".*", "$1") : "";
    }

    private static String extractBarcode(String json) {
        return json.contains("\"barcode\"") ? json.replaceAll(".*\"barcode\"\\s*:\\s*\"([^\"]+)\".*", "$1") : "";
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
