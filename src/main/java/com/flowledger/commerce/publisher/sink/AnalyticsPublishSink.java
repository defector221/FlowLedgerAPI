package com.flowledger.commerce.publisher.sink;

import com.flowledger.commerce.publisher.model.ProductPublishedSnapshot;
import com.flowledger.commerce.publisher.model.StorePublishedSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Future: feed publish events to analytics pipeline. */
@Component
@Order(400)
public class AnalyticsPublishSink implements CommercePublishSink {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsPublishSink.class);

    @Override
    public void onStorePublished(StorePublishedSnapshot snapshot) {
        log.debug("Analytics feed hook store={}", snapshot.storeId());
    }

    @Override
    public void onProductPublished(ProductPublishedSnapshot snapshot) {
        log.debug("Analytics feed hook product={}", snapshot.productId());
    }
}
