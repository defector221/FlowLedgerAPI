package com.flowledger.commerce.api;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.fulfillment.FulfillmentService;
import com.flowledger.commerce.fulfillment.scan_go.ScanGoService;
import com.flowledger.common.dto.ApiResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/scan")
public class CommerceScanController {
    private final ScanGoService scanGo;
    private final FulfillmentService fulfillment;

    public CommerceScanController(ScanGoService scanGo, FulfillmentService fulfillment) {
        this.scanGo = scanGo;
        this.fulfillment = fulfillment;
    }

    @PostMapping("/session")
    @PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
    public ApiResponse<CommerceDtos.ScanSessionResponse> openSession(
            @RequestBody CommerceDtos.OpenScanSessionRequest request) {
        return ApiResponse.of(scanGo.openSession(request));
    }

    @PostMapping("/session/{sessionId}/item")
    @PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
    public ApiResponse<CommerceDtos.ScanSessionResponse> scanItem(
            @PathVariable UUID sessionId, @RequestBody CommerceDtos.ScanItemRequest request) {
        return ApiResponse.of(scanGo.scanItem(sessionId, request));
    }

    @PostMapping("/session/{sessionId}/payment")
    @PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
    public ApiResponse<CommerceDtos.ScanSessionResponse> pay(@PathVariable UUID sessionId) {
        return ApiResponse.of(scanGo.markPaid(sessionId));
    }

    @PostMapping("/session/{sessionId}/exit")
    @PreAuthorize("hasAuthority('COMMERCE_CUSTOMER')")
    public ApiResponse<CommerceDtos.ScanExitResponse> exit(@PathVariable UUID sessionId) {
        return ApiResponse.of(scanGo.generateExitToken(sessionId));
    }

    @PostMapping("/exit/verify")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_MANAGE')")
    public ApiResponse<CommerceDtos.CommerceOrderResponse> verifyExit(@RequestBody CommerceDtos.VerifyCollectRequest request) {
        return ApiResponse.of(scanGo.verifyExit(request.token()));
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('COMMERCE_FULFILLMENT_VIEW')")
    public ApiResponse<java.util.List<CommerceDtos.ScanSessionResponse>> activeSessions(@RequestParam UUID storeId) {
        return ApiResponse.of(scanGo.listActiveSessions(storeId).stream()
                .map(s -> new CommerceDtos.ScanSessionResponse(
                        s.getId(),
                        s.getStoreId(),
                        s.getStatus().name(),
                        s.getCurrency(),
                        s.getSubtotal(),
                        s.getTaxTotal(),
                        s.getGrandTotal(),
                        s.getItemCount(),
                        java.util.List.of()))
                .toList());
    }
}
