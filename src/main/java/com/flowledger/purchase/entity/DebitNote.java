package com.flowledger.purchase.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "debit_notes")
@Getter
@Setter
@NoArgsConstructor
public class DebitNote extends AuditedEntity {
    @Column(name = "debit_note_number")
    private String debitNoteNumber;

    private UUID purchaseReturnId, purchaseInvoiceId, supplierId;
    private LocalDate debitNoteDate;
    private BigDecimal amount;
    private String status = "ISSUED";

    @Column(columnDefinition = "text")
    private String notes;
}
