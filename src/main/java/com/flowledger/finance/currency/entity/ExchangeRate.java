package com.flowledger.finance.currency.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "exchange_rates",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_exchange_rates",
                        columnNames = {"organization_id", "from_currency", "to_currency", "effective_date"}))
@Getter
@Setter
@NoArgsConstructor
public class ExchangeRate extends AuditedEntity {
    @Column(name = "from_currency", nullable = false, length = 10)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false, length = 10)
    private String toCurrency;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
}
