package com.flowledger.commerce.publisher.sink;

import com.flowledger.commerce.publisher.model.BrandPublishedSnapshot;
import com.flowledger.commerce.publisher.model.CategoryPublishedSnapshot;
import com.flowledger.commerce.publisher.model.ProductPublishedSnapshot;
import com.flowledger.commerce.publisher.model.StorePublishedSnapshot;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Future: feed product/store changes to recommendation engine. */
@Component
@Order(300)
public class RecommendationPublishSink implements CommercePublishSink {
    private static final Logger log = LoggerFactory.getLogger(RecommendationPublishSink.class);

    @Override
    public void onProductPublished(ProductPublishedSnapshot snapshot) {
        log.debug("Recommendation feed hook product={} store={}", snapshot.productId(), snapshot.storeId());
    }
}
