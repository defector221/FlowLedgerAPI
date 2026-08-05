package com.flowledger.commerce.ai;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import com.flowledger.ai.provider.AIProviderRegistry;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import com.flowledger.commerce.publisher.repository.MarketplaceProductIndexRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnAiEnabled
public class CommerceAiService {
    private final MarketplaceProductIndexRepository products;
    private final CommerceOrderRepository orders;
    private final AIProviderRegistry providers;

    public CommerceAiService(
            MarketplaceProductIndexRepository products,
            CommerceOrderRepository orders,
            AIProviderRegistry providers) {
        this.products = products;
        this.orders = orders;
        this.providers = providers;
    }

    @Transactional(readOnly = true)
    public List<AiDtos.CommerceSimilarProductResponse> similarProducts(UUID storeId, UUID productIndexId, int limit) {
        MarketplaceProductIndex seed = products
                .findById(productIndexId)
                .filter(p -> p.getStoreId().equals(storeId) && p.isPublished())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found for store"));
        List<MarketplaceProductIndex> storeProducts = products.findByStoreIdAndPublishedTrue(storeId);
        List<Float> seedVector = embed(seed.getName() + " " + nullToEmpty(seed.getBrand()) + " " + nullToEmpty(seed.getCategoryName()));
        List<Scored> scored = new ArrayList<>();
        for (MarketplaceProductIndex candidate : storeProducts) {
            if (candidate.getId().equals(seed.getId())) {
                continue;
            }
            List<Float> vector = embed(candidate.getName() + " " + nullToEmpty(candidate.getBrand()) + " "
                    + nullToEmpty(candidate.getCategoryName()));
            scored.add(new Scored(candidate, cosine(seedVector, vector)));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.stream()
                .limit(Math.max(1, limit))
                .map(s -> new AiDtos.CommerceSimilarProductResponse(
                        s.product().getId(),
                        s.product().getStoreId(),
                        s.product().getProductId(),
                        s.product().getName(),
                        s.product().getSku(),
                        s.score()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiDtos.CommerceSimilarProductResponse> semanticSearch(UUID storeId, String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<Float> queryVector = embed(query);
        List<Scored> scored = new ArrayList<>();
        for (MarketplaceProductIndex product : products.findByStoreIdAndPublishedTrue(storeId)) {
            List<Float> vector = embed(product.getName() + " " + nullToEmpty(product.getBrand()) + " "
                    + nullToEmpty(product.getCategoryName()) + " " + nullToEmpty(product.getSearchText()));
            scored.add(new Scored(product, cosine(queryVector, vector)));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.stream()
                .limit(Math.max(1, limit))
                .map(s -> new AiDtos.CommerceSimilarProductResponse(
                        s.product().getId(),
                        s.product().getStoreId(),
                        s.product().getProductId(),
                        s.product().getName(),
                        s.product().getSku(),
                        s.score()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AiDtos.CommerceSupportResponse support(UUID customerId, AiDtos.CommerceSupportRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        List<String> tools = new ArrayList<>();
        StringBuilder answer = new StringBuilder();
        if (request.orderId() != null) {
            tools.add("order_lookup");
            CommerceOrder order = orders
                    .findByIdAndCustomerId(request.orderId(), customerId)
                    .orElse(null);
            if (order == null) {
                answer.append("I could not find that order for your account. ");
            } else {
                answer.append("Order ")
                        .append(order.getOrderNumber())
                        .append(" is currently ")
                        .append(order.getStatus())
                        .append(" with total ")
                        .append(order.getCurrency())
                        .append(' ')
                        .append(order.getGrandTotal())
                        .append(". ");
            }
        }
        String lower = request.message().toLowerCase(Locale.ROOT);
        if (lower.contains("return")) {
            tools.add("returns_faq");
            answer.append("Returns: open Fulfillment History in admin, or contact the store with your order number. ");
            answer.append("Partial returns are supported when an ERP invoice exists. ");
        }
        if (lower.contains("delivery") || lower.contains("track")) {
            tools.add("delivery_faq");
            answer.append("Delivery updates appear after dispatch. Pickup/collect codes are shown on the order when ready. ");
        }
        if (answer.isEmpty()) {
            tools.add("general");
            answer.append("I can help with order status, delivery, and returns. Include your order id for specifics.");
        }
        return new AiDtos.CommerceSupportResponse(answer.toString().trim(), tools);
    }

    private List<Float> embed(String text) {
        try {
            return providers.active().embed(text);
        } catch (Exception e) {
            return localEmbed(text);
        }
    }

    private static List<Float> localEmbed(String text) {
        List<Float> out = new ArrayList<>(32);
        int hash = text == null ? 0 : text.hashCode();
        for (int i = 0; i < 32; i++) {
            out.add(((hash >> (i % 24)) & 0xff) / 255f);
        }
        return out;
    }

    private static double cosine(List<Float> a, List<Float> b) {
        int n = Math.min(a.size(), b.size());
        if (n == 0) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < n; i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private record Scored(MarketplaceProductIndex product, double score) {}
}
