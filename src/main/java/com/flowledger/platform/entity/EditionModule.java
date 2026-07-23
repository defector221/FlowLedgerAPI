package com.flowledger.platform.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "edition_modules")
@IdClass(EditionModule.Pk.class)
@Getter
@Setter
@NoArgsConstructor
public class EditionModule {
    @Id
    @Column(name = "edition_code", length = 32)
    private String editionCode;

    @Id
    @Column(name = "module_code", length = 64)
    private String moduleCode;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Pk implements Serializable {
        private String editionCode;
        private String moduleCode;
    }
}
