package com.flowledger.retail.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Getter
@Setter
public abstract class RetailAuditedEntity extends AuditedEntity {
    @Version
    private Long version;

    @Column(nullable = false)
    private boolean deleted;
}
