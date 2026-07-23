package com.flowledger.retail.entity;

import com.flowledger.retail.domain.RetailEnums.ShiftStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_shifts")
@Getter
@Setter
@NoArgsConstructor
public class RetailShift extends RetailAuditedEntity {
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "counter_id", nullable = false)
    private UUID counterId;

    @Column(name = "terminal_id")
    private UUID terminalId;

    @Column(name = "cashier_id", nullable = false)
    private UUID cashierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShiftStatus status = ShiftStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "opening_float", nullable = false, precision = 18, scale = 2)
    private BigDecimal openingFloat = BigDecimal.ZERO;

    @Column(name = "closing_cash", precision = 18, scale = 2)
    private BigDecimal closingCash;

    @Column(name = "expected_cash", precision = 18, scale = 2)
    private BigDecimal expectedCash;

    @Column(precision = 18, scale = 2)
    private BigDecimal variance;

    @Column(columnDefinition = "text")
    private String notes;
}
