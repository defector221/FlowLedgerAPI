package com.flowledger.commerce.engagement.repository;

import com.flowledger.commerce.engagement.entity.CommerceSavedListItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceSavedListItemRepository extends JpaRepository<CommerceSavedListItem, UUID> {
    List<CommerceSavedListItem> findByListIdOrderByCreatedAtAsc(UUID listId);
}
