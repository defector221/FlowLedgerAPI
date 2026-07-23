package com.flowledger.platform.repository;

import com.flowledger.platform.entity.EditionModule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EditionModuleRepository extends JpaRepository<EditionModule, EditionModule.Pk> {
    List<EditionModule> findByEditionCode(String editionCode);
}
