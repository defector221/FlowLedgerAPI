package com.flowledger.product.controller;

import com.flowledger.product.dto.PublicProductDtos.PublicProductCard;
import com.flowledger.product.service.PublicProductService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/products")
public class PublicProductController {
    private final PublicProductService publicProducts;

    public PublicProductController(PublicProductService publicProducts) {
        this.publicProducts = publicProducts;
    }

    @GetMapping("/{productId}")
    public PublicProductCard get(@PathVariable UUID productId) {
        return publicProducts.getPublishedProduct(productId);
    }
}
