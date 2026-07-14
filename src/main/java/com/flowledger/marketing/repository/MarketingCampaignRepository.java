package com.flowledger.marketing.repository;

import com.flowledger.marketing.entity.MarketingCampaign;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketingCampaignRepository extends JpaRepository<MarketingCampaign, UUID> {
    List<MarketingCampaign> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<MarketingCampaign> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Query(
            """
            select c from MarketingCampaign c
            where c.status in ('SCHEDULED', 'SENDING')
              and (c.scheduledAt is null or c.scheduledAt <= :now)
            order by c.createdAt
            """)
    List<MarketingCampaign> findDueCampaigns(@Param("now") OffsetDateTime now);
}
