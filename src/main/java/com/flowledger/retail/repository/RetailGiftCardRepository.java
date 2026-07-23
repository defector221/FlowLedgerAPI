package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailGiftCard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailGiftCardRepository extends JpaRepository<RetailGiftCard, UUID> {
    List<RetailGiftCard> findByOrganizationIdAndDeletedFalseOrderByCreatedAtDesc(UUID organizationId);

    Optional<RetailGiftCard> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    Optional<RetailGiftCard> findByOrganizationIdAndCardNumberAndDeletedFalse(
            UUID organizationId, String cardNumber);

    boolean existsByOrganizationIdAndCardNumberAndDeletedFalse(UUID organizationId, String cardNumber);
}
