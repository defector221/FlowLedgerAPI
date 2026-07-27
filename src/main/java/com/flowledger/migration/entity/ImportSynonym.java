package com.flowledger.migration.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "import_synonyms")
@Getter
@Setter
@NoArgsConstructor
public class ImportSynonym {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String synonym;

    @Column(name = "target_field", nullable = false, length = 200)
    private String targetField;

    @Column(length = 50)
    private String module;
}
