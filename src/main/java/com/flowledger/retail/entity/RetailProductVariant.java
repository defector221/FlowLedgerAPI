package com.flowledger.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "retail_product_variants")
@Getter
@Setter
@NoArgsConstructor
public class RetailProductVariant extends RetailAuditedEntity {
    @Column(name = "parent_product_id", nullable = false)
    private UUID parentProductId;

    private String sku;
    private String barcode;
    private String color;
    private String size;
    private String weight;
    private String capacity;
    private String pattern;
    private String material;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes_json", nullable = false, columnDefinition = "jsonb")
    private String attributesJson = "{}";

    @Column(name = "selling_price", precision = 18, scale = 4)
    private BigDecimal sellingPrice;

    @Column(precision = 18, scale = 4)
    private BigDecimal mrp;

    @Column(nullable = false)
    private boolean active = true;
}
