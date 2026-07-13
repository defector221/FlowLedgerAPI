package com.flowledger.marketing.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "marketing_sequences")
@Getter
@Setter
@NoArgsConstructor
public class MarketingSequence extends AuditedEntity {
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private String status = "DRAFT";

    @Column(name = "trigger_type", nullable = false)
    private String triggerType = "MANUAL";

    @Version
    private Long version;

    @OneToMany(mappedBy = "sequence", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    private List<MarketingSequenceStep> steps = new ArrayList<>();
}
