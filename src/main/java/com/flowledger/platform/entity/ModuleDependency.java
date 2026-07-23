package com.flowledger.platform.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "module_dependencies")
@IdClass(ModuleDependency.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class ModuleDependency {
    @Id
    @Column(name = "module_code", length = 64)
    private String moduleCode;

    @Id
    @Column(name = "depends_on_code", length = 64)
    private String dependsOnCode;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private String moduleCode;
        private String dependsOnCode;
    }
}
