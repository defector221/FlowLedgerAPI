package com.flowledger.organization.repository;
import com.flowledger.organization.entity.Organization; import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface OrganizationRepository extends JpaRepository<Organization,UUID> {}
