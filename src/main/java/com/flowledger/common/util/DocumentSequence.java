package com.flowledger.common.util;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "document_sequences")
@Getter
@Setter
public class DocumentSequence {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID organizationId;

    @Column(name = "branch_id")
    private UUID branchId;

    private String documentType;
    private String financialYear;
    private String prefix;
    private long nextValue = 1;

    @Version
    private Long version;
}
