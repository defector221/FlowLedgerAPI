package com.flowledger.tax.repository;

import com.flowledger.tax.entity.OrganizationTaxSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationTaxSettingsRepository extends JpaRepository<OrganizationTaxSettings, UUID> {}
