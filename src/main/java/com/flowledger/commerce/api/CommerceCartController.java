package com.flowledger.commerce.api;

import com.flowledger.commerce.cart.CartService;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.common.dto.ApiResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/carts")
@PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
public class CommerceCartController {
    private final CartService cartService;

    public CommerceCartController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping
    public ApiResponse<CommerceDtos.CartResponse> createOrGet(@Valid @RequestBody CommerceDtos.CreateCartRequest request) {
        return ApiResponse.of(cartService.getOrCreateCart(request.storeId()));
    }

    @GetMapping("/active")
    public ApiResponse<CommerceDtos.CartResponse> activeCart(@RequestParam UUID storeId) {
        return ApiResponse.of(cartService.getOrCreateCart(storeId));
    }

    @GetMapping("/{cartId}")
    public ApiResponse<CommerceDtos.CartResponse> getCart(@PathVariable UUID cartId) {
        return ApiResponse.of(cartService.getCart(cartId));
    }

    @PostMapping("/{cartId}/items")
    public ApiResponse<CommerceDtos.CartResponse> addItem(
            @PathVariable UUID cartId, @Valid @RequestBody CommerceDtos.AddCartItemRequest request) {
        return ApiResponse.of(cartService.addItem(cartId, request));
    }

    @PatchMapping("/{cartId}/items/{itemId}")
    public ApiResponse<CommerceDtos.CartResponse> updateItem(
            @PathVariable UUID cartId,
            @PathVariable UUID itemId,
            @Valid @RequestBody CommerceDtos.UpdateCartItemRequest request) {
        return ApiResponse.of(cartService.updateItemQuantity(cartId, itemId, request.quantity()));
    }

    @DeleteMapping("/{cartId}/items/{itemId}")
    public ApiResponse<CommerceDtos.CartResponse> removeItem(@PathVariable UUID cartId, @PathVariable UUID itemId) {
        return ApiResponse.of(cartService.removeItem(cartId, itemId));
    }
}
