package com.flowledger.labels.repository;

import com.flowledger.labels.entity.LabelTemplateField;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LabelTemplateFieldRepository extends JpaRepository<LabelTemplateField, UUID> {
    @Query(
            """
            select f from LabelTemplateField f
            where f.templateId = :templateId
            order by f.zIndex asc
            """)
    List<LabelTemplateField> findByTemplateIdOrderByZIndexAsc(@Param("templateId") UUID templateId);

    void deleteByTemplateId(UUID templateId);
}
