package com.flowledger.product.repository;

import com.flowledger.product.entity.Category;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {
    Optional<Category> findByIdAndOrganizationId(UUID id, UUID org);

    @Query(
            """
            select case when count(c) > 0 then true else false end
            from Category c
            where c.organizationId = :organizationId
              and lower(trim(c.name)) = lower(trim(:name))
            """)
    boolean existsByOrganizationIdAndNameIgnoreCase(
            @Param("organizationId") UUID organizationId, @Param("name") String name);

    @Query(
            """
            select case when count(c) > 0 then true else false end
            from Category c
            where c.organizationId = :organizationId
              and lower(trim(c.name)) = lower(trim(:name))
              and c.id <> :id
            """)
    boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
            @Param("organizationId") UUID organizationId, @Param("name") String name, @Param("id") UUID id);
}
