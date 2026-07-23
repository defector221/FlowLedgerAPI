package com.flowledger.platform.repository;

import com.flowledger.platform.entity.ModuleFeature;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleFeatureRepository extends JpaRepository<ModuleFeature, UUID> {
    List<ModuleFeature> findByModuleCodeOrderByFeatureCodeAsc(String moduleCode);

    List<ModuleFeature> findAllByOrderByModuleCodeAscFeatureCodeAsc();

    Optional<ModuleFeature> findByModuleCodeAndFeatureCode(String moduleCode, String featureCode);
}
