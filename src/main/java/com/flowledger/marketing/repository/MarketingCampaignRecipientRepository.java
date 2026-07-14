package com.flowledger.marketing.repository;

import com.flowledger.marketing.entity.MarketingCampaignRecipient;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingCampaignRecipientRepository extends JpaRepository<MarketingCampaignRecipient, UUID> {
    Page<MarketingCampaignRecipient> findByCampaignIdOrderByCreatedAtAsc(UUID campaignId, Pageable pageable);

    List<MarketingCampaignRecipient> findTop50ByCampaignIdAndStatusOrderByCreatedAtAsc(UUID campaignId, String status);

    long countByCampaignIdAndStatus(UUID campaignId, String status);

    void deleteByCampaignId(UUID campaignId);
}
