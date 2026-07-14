package com.flowledger.product.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
        name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uq_products_org_sku", columnNames = {"organization_id", "sku"}))
@Getter
@Setter
@NoArgsConstructor
public class Product extends AuditedEntity {
    public enum ItemType {
        PRODUCT,
        SERVICE
    }

    @Column(name = "item_type", nullable = false)
    private String itemType = "PRODUCT";

    @Column(nullable = false)
    private String sku;

    private String barcode;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "category_id")
    private UUID categoryId;

    private String brand;

    @Column(name = "hsn_sac_code")
    private String hsnSacCode;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "purchase_price", nullable = false)
    private BigDecimal purchasePrice = BigDecimal.ZERO;

    @Column(name = "selling_price", nullable = false)
    private BigDecimal sellingPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal mrp = BigDecimal.ZERO;

    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    @Column(name = "opening_stock", nullable = false)
    private BigDecimal openingStock = BigDecimal.ZERO;

    @Column(name = "minimum_stock_level", nullable = false)
    private BigDecimal minimumStockLevel = BigDecimal.ZERO;

    @Column(name = "maximum_stock_level")
    private BigDecimal maximumStockLevel;

    @Column(name = "reorder_level", nullable = false)
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "batch_tracking", nullable = false)
    private boolean batchTracking;

    @Column(name = "serial_tracking", nullable = false)
    private boolean serialTracking;

    @Column(name = "expiry_tracking", nullable = false)
    private boolean expiryTracking;

    @Column(name = "image_object_key")
    private String imageObjectKey;

    @Column(nullable = false)
    private boolean active = true;
}
