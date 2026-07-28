package com.flowledger.tax.entity;

import com.flowledger.common.entity.AuditedEntity;
import com.flowledger.product.entity.TaxType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tax_rules")
@Getter
@Setter
@NoArgsConstructor
public class TaxRule extends AuditedEntity {
    @Column(name = "tax_category_id", nullable = false)
    private UUID taxCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type", nullable = false, length = 20)
    private TaxType taxType = TaxType.GST;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode = "IN";

    @Column(name = "state_code", length = 10)
    private String stateCode;

    @Column(name = "cgst_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal cgstRate = BigDecimal.ZERO;

    @Column(name = "sgst_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal sgstRate = BigDecimal.ZERO;

    @Column(name = "igst_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal igstRate = BigDecimal.ZERO;

    @Column(name = "cess_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal cessRate = BigDecimal.ZERO;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean inclusive = false;

    @Column(nullable = false)
    private int priority = 0;

    @Column(name = "reverse_charge", nullable = false)
    private boolean reverseCharge = false;

    @Column(name = "zero_rated", nullable = false)
    private boolean zeroRated = false;

    @Column(nullable = false)
    private boolean exempt = false;

    @Column(name = "nil_rated", nullable = false)
    private boolean nilRated = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "version_label", length = 50)
    private String versionLabel;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "published_by")
    private UUID publishedBy;
}
