package com.flowledger.common.entity;

import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AuditableEntity extends BaseEntity {
    @Version
    private Long version;
}
