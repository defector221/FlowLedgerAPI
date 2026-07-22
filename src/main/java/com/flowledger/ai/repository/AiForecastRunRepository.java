package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiForecastRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiForecastRunRepository extends JpaRepository<AiForecastRun, UUID> {
    List<AiForecastRun> findByOrganizationIdAndForecastTypeOrderByCreatedAtDesc(
            UUID organizationId, String forecastType);

    List<AiForecastRun> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
