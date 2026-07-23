package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailStorePriceList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailStorePriceListRepository extends JpaRepository<RetailStorePriceList, UUID> {
    List<RetailStorePriceList> findByOrganizationIdAndStoreId(UUID organizationId, UUID storeId);

    Optional<RetailStorePriceList> findByOrganizationIdAndStoreIdAndPriceListId(
            UUID organizationId, UUID storeId, UUID priceListId);

    void deleteByOrganizationIdAndStoreId(UUID organizationId, UUID storeId);
}
