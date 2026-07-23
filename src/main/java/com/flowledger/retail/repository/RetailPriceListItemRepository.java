package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailPriceListItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailPriceListItemRepository extends JpaRepository<RetailPriceListItem, UUID> {
    List<RetailPriceListItem> findByOrganizationIdAndPriceListId(UUID organizationId, UUID priceListId);

    List<RetailPriceListItem> findByOrganizationIdAndPriceListIdAndProductId(
            UUID organizationId, UUID priceListId, UUID productId);

    Optional<RetailPriceListItem> findByIdAndOrganizationId(UUID id, UUID organizationId);

    void deleteByPriceListId(UUID priceListId);
}
