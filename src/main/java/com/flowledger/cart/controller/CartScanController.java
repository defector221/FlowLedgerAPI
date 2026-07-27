package com.flowledger.cart.controller;

import static com.flowledger.cart.dto.CartDtos.*;

import com.flowledger.cart.service.CartScanService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartScanController {
    private final CartScanService cartScanService;

    public CartScanController(CartScanService cartScanService) {
        this.cartScanService = cartScanService;
    }

    @PostMapping("/scan")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public CartScanResponse scan(@Valid @RequestBody CartScanRequest request) {
        return cartScanService.scan(request);
    }

    @PostMapping("/scan/confirm")
    @PreAuthorize("hasAuthority('RETAIL_POS')")
    public CartScanResponse confirm(@Valid @RequestBody CartScanConfirmRequest request) {
        return cartScanService.confirm(request);
    }
}
