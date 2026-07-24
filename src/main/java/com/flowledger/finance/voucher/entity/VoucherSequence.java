package com.flowledger.finance.voucher.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "voucher_sequences")
@Getter
@Setter
@NoArgsConstructor
public class VoucherSequence {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "voucher_type", nullable = false)
    private String voucherType;

    @Column(name = "financial_year", nullable = false)
    private String financialYear;

    @Column(nullable = false)
    private String prefix;

    @Column(name = "next_number", nullable = false)
    private long nextNumber = 1;

    @Version
    private Long version;
}
