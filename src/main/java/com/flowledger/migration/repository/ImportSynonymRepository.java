package com.flowledger.migration.repository;

import com.flowledger.migration.entity.ImportSynonym;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ImportSynonymRepository extends JpaRepository<ImportSynonym, UUID> {
    @Query(
            """
            select s from ImportSynonym s
            where s.module is null or s.module = :module
            """)
    List<ImportSynonym> findForModule(String module);
}
