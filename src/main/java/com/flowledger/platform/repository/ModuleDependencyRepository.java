package com.flowledger.platform.repository;

import com.flowledger.platform.entity.ModuleDependency;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleDependencyRepository extends JpaRepository<ModuleDependency, ModuleDependency.Pk> {
    List<ModuleDependency> findByModuleCode(String moduleCode);

    List<ModuleDependency> findAll();
}
