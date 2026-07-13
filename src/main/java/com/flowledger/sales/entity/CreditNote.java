package com.flowledger.sales.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "credit_notes")
@Getter
@Setter
@NoArgsConstructor
public class CreditNote extends AuditedEntity {
    @Column(nullable = false)
    private String creditNoteNumber;

    private UUID salesReturnId, salesInvoiceId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private LocalDate creditNoteDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status = "ISSUED";

    @Column(columnDefinition = "text")
    private String notes;
}
