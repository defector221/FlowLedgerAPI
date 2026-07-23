package com.flowledger.retail.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_product_barcodes")
@Getter
@Setter
@NoArgsConstructor
public class RetailProductBarcode extends AuditedEntity {
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(nullable = false)
    private String barcode;

    @Column(name = "barcode_type", nullable = false)
    private String barcodeType = "EAN13";

    @Column(name = "is_primary", nullable = false)
    private boolean primary;
}
