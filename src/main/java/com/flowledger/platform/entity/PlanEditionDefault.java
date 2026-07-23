package com.flowledger.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "plan_edition_defaults")
@Getter
@Setter
@NoArgsConstructor
public class PlanEditionDefault {
    @Id
    @Column(name = "plan_code", length = 32)
    private String planCode;

    @Column(name = "edition_code", nullable = false, length = 32)
    private String editionCode;
}
