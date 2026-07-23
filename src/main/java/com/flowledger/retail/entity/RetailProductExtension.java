package com.flowledger.retail.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "retail_product_extensions")
@Getter
@Setter
@NoArgsConstructor
public class RetailProductExtension extends AuditedEntity {
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "collection_id")
    private UUID collectionId;

    private String season;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes_json", nullable = false, columnDefinition = "jsonb")
    private String attributesJson = "{}";
}
