package com.flowledger.commerce.order;

import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.sales.dto.SalesDtos;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CommerceOrderBuilder {
    private final ProductRepository products;

    public CommerceOrderBuilder(ProductRepository products) {
        this.products = products;
    }

    public List<SalesDtos.Item> buildInvoiceItems(UUID organizationId, List<CommerceCartItem> items) {
        return CommerceTenantScope.run(organizationId, () -> {
            List<SalesDtos.Item> result = new ArrayList<>();
            for (CommerceCartItem line : items) {
                Product product = products
                        .findByIdAndOrganizationId(line.getProductId(), organizationId)
                        .orElseThrow(() -> new IllegalArgumentException("Product not found"));
                BigDecimal rate = line.getLineSubtotal().divide(line.getQuantity(), 4, java.math.RoundingMode.HALF_UP);
                result.add(new SalesDtos.Item(
                        line.getProductId(),
                        product.getName(),
                        product.getHsnSacCode(),
                        line.getQuantity(),
                        product.getUnitId(),
                        rate,
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        null,
                        null));
            }
            return result;
        });
    }
}
