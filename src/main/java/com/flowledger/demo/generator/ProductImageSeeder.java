package com.flowledger.demo.generator;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.demo.DemoSeedContext;
import com.flowledger.demo.images.CategoryImageProvider;
import com.flowledger.demo.images.DemoImageCategoryMapper;
import com.flowledger.demo.images.DemoImageResource;
import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.util.DemoIsolatedWork;
import com.flowledger.demo.util.ProgressLogger;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductImageRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.product.service.ProductImageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProductImageSeeder {
    private static final Logger log = LoggerFactory.getLogger(ProductImageSeeder.class);
    private static final String[] GALLERY_ROLES = {"MAIN", "GALLERY", "SIDE", "BACK"};

    private final CategoryImageProvider images;
    private final ProductRepository products;
    private final ProductImageRepository productImages;
    private final ProductImageService productImageService;
    private final DemoIsolatedWork isolated;

    public ProductImageSeeder(
            CategoryImageProvider images,
            ProductRepository products,
            ProductImageRepository productImages,
            ProductImageService productImageService,
            DemoIsolatedWork isolated) {
        this.images = images;
        this.products = products;
        this.productImages = productImages;
        this.productImageService = productImageService;
        this.isolated = isolated;
    }

    public void generate(DemoSeedContext ctx, ProgressLogger progress) {
        DemoBlueprint blueprint = ctx.getBlueprint();
        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
        progress.stage("Attaching product images");

        List<UUID> productIds = ctx.getProductIds();
        if (productIds.isEmpty()) {
            progress.done("Product images skipped (no products)");
            return;
        }

        AtomicInteger uploaded = new AtomicInteger();
        AtomicInteger reused = new AtomicInteger();
        int total = productIds.size();
        int i = 0;
        for (UUID productId : productIds) {
            i++;
            Product product = products.findById(productId).orElse(null);
            if (product == null) {
                continue;
            }
            if (product.getItemType() != null && "SERVICE".equalsIgnoreCase(product.getItemType())) {
                continue;
            }
            if (!productImages.findByOrganizationIdAndProductIdOrderBySortOrderAsc(ctx.getOrganizationId(), productId)
                    .isEmpty()) {
                reused.incrementAndGet();
                continue;
            }
            String folder = DemoImageCategoryMapper.resolve(
                    product.getName(), blueprint.catalogStrategy(), ctx.getScenario());
            List<DemoImageResource> pool = images.imagesFor(folder);
            if (pool.isEmpty()) {
                DemoImageResource def = images.defaultImage();
                if (def != null) {
                    pool = List.of(def);
                }
            }
            if (pool.isEmpty()) {
                continue;
            }
            int galleryCount = Math.min(3, pool.size());
            for (int g = 0; g < galleryCount; g++) {
                DemoImageResource res = pool.get((i + g) % pool.size());
                String role = GALLERY_ROLES[Math.min(g, GALLERY_ROLES.length - 1)];
                final boolean primary = g == 0;
                try {
                    boolean attached = isolated.call(() -> {
                        TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
                        try {
                            return attach(ctx, productId, res, role, primary);
                        } catch (Exception ex) {
                            throw new RuntimeException(ex);
                        }
                    });
                    if (attached) {
                        uploaded.incrementAndGet();
                    } else {
                        reused.incrementAndGet();
                    }
                } catch (RuntimeException ex) {
                    log.debug("Image attach failed for {}: {}", productId, ex.getMessage());
                }
                TenantContext.set(ctx.getOrganizationId(), ctx.getAdminUserId());
            }
            if (i % 25 == 0 || i == total) {
                progress.progress("Product images", i, total);
            }
        }
        ctx.getMeta().put("imagesUploaded", uploaded.get());
        ctx.getMeta().put("imagesReused", reused.get());
        progress.done("Product images (uploaded=" + uploaded.get() + ", reused=" + reused.get() + ")");
    }

    private boolean attach(
            DemoSeedContext ctx, UUID productId, DemoImageResource res, String role, boolean primary) throws Exception {
        byte[] bytes;
        try (InputStream in = res.openStream()) {
            bytes = in.readAllBytes();
        }
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String checksum = HexFormat.of().formatHex(md.digest(bytes));

        var existing = productImages.findFirstByOrganizationIdAndChecksum(ctx.getOrganizationId(), checksum);
        if (existing.isPresent()
                && existing.get().getProductId().equals(productId)) {
            return false;
        }

        // Idempotent reuse of stored object for same checksum in org: still link new product_images row pointing at
        // copied stream upload (simpler than cross-product key share). Skip re-upload only if product already has this
        // checksum.
        if (productImages.findByOrganizationIdAndProductIdOrderBySortOrderAsc(ctx.getOrganizationId(), productId)
                .stream()
                .anyMatch(img -> checksum.equals(img.getChecksum()))) {
            return false;
        }

        productImageService.uploadStream(
                productId,
                new ByteArrayInputStream(bytes),
                res.filename(),
                res.contentType(),
                bytes.length,
                primary,
                checksum,
                role);
        return true;
    }
}
