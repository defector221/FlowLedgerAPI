package com.flowledger.platform.repository;

import com.flowledger.platform.entity.PlatformModule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformModuleRepository extends JpaRepository<PlatformModule, String> {
    List<PlatformModule> findByStatusOrderByCategoryAscDisplayNameAsc(String status);

    List<PlatformModule> findAllByOrderByCategoryAscDisplayNameAsc();
}
