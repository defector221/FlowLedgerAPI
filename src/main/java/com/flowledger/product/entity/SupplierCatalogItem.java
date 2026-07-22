package com.flowledger.product.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "supplier_catalog_items")
@Getter
@Setter
@NoArgsConstructor
public class SupplierCatalogItem extends AuditedEntity {
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "supplier_sku")
    private String supplierSku;

    @Column(name = "supplier_product_name")
    private String supplierProductName;

    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal purchasePrice;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(precision = 19, scale = 4)
    private BigDecimal moq;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(nullable = false)
    private boolean preferred;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deleted;

    @Version
    @Column(nullable = false)
    private long version;
}
