package com.flowledger.commerce.engagement;

import com.flowledger.commerce.engagement.entity.CommerceReview;
import com.flowledger.commerce.engagement.entity.CommerceSavedList;
import com.flowledger.commerce.engagement.entity.CommerceSavedListItem;
import com.flowledger.commerce.engagement.entity.CommerceWishlistItem;
import com.flowledger.commerce.engagement.repository.CommerceReviewRepository;
import com.flowledger.commerce.engagement.repository.CommerceSavedListItemRepository;
import com.flowledger.commerce.engagement.repository.CommerceSavedListRepository;
import com.flowledger.commerce.engagement.repository.CommerceWishlistItemRepository;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EngagementService {
    private final CommerceWishlistItemRepository wishlist;
    private final CommerceSavedListRepository savedLists;
    private final CommerceSavedListItemRepository savedListItems;
    private final CommerceReviewRepository reviews;

    public EngagementService(
            CommerceWishlistItemRepository wishlist,
            CommerceSavedListRepository savedLists,
            CommerceSavedListItemRepository savedListItems,
            CommerceReviewRepository reviews) {
        this.wishlist = wishlist;
        this.savedLists = savedLists;
        this.savedListItems = savedListItems;
        this.reviews = reviews;
    }

    public CommerceWishlistItem addWishlist(UUID customerId, UUID organizationId, UUID storeId, UUID productId) {
        CommerceWishlistItem item = new CommerceWishlistItem();
        item.setCustomerId(customerId);
        item.setOrganizationId(organizationId);
        item.setStoreId(storeId);
        item.setProductId(productId);
        return wishlist.save(item);
    }

    public void removeWishlist(UUID customerId, UUID storeId, UUID productId) {
        wishlist.deleteByCustomerIdAndStoreIdAndProductId(customerId, storeId, productId);
    }

    @Transactional(readOnly = true)
    public List<CommerceWishlistItem> listWishlist(UUID customerId, UUID organizationId) {
        return wishlist.findByCustomerIdAndOrganizationIdOrderByCreatedAtDesc(customerId, organizationId);
    }

    public CommerceSavedList createList(UUID customerId, UUID organizationId, String name) {
        CommerceSavedList list = new CommerceSavedList();
        list.setCustomerId(customerId);
        list.setOrganizationId(organizationId);
        list.setName(name);
        return savedLists.save(list);
    }

    public CommerceSavedListItem addListItem(UUID listId, UUID customerId, UUID productId, BigDecimal quantity) {
        CommerceSavedList list = savedLists.findById(listId)
                .orElseThrow(() -> new ResourceNotFoundException("List not found"));
        if (!list.getCustomerId().equals(customerId)) {
            throw new ResourceNotFoundException("List not found");
        }
        CommerceSavedListItem item = new CommerceSavedListItem();
        item.setListId(listId);
        item.setProductId(productId);
        item.setQuantity(quantity != null ? quantity : BigDecimal.ONE);
        return savedListItems.save(item);
    }

    @Transactional(readOnly = true)
    public List<CommerceSavedList> listSavedLists(UUID customerId, UUID organizationId) {
        return savedLists.findByCustomerIdAndOrganizationIdOrderByCreatedAtDesc(customerId, organizationId);
    }

    public CommerceReview submitReview(
            UUID customerId,
            UUID organizationId,
            String reviewType,
            UUID storeId,
            UUID productId,
            UUID orderId,
            int rating,
            String comment) {
        CommerceReview review = new CommerceReview();
        review.setCustomerId(customerId);
        review.setOrganizationId(organizationId);
        review.setReviewType(reviewType);
        review.setStoreId(storeId);
        review.setProductId(productId);
        review.setOrderId(orderId);
        review.setRating(rating);
        review.setComment(comment);
        review.setModerated(false);
        return reviews.save(review);
    }

    @Transactional(readOnly = true)
    public List<CommerceReview> listStoreReviews(UUID organizationId, UUID storeId) {
        return reviews.findByOrganizationIdAndStoreIdOrderByCreatedAtDesc(organizationId, storeId);
    }
}
