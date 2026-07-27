package com.flowledger.product.controller;

import com.flowledger.product.dto.ProductIdentificationDtos.ImageResponse;
import com.flowledger.product.dto.ProductIdentificationDtos.QrCodeRequest;
import com.flowledger.product.dto.ProductIdentificationDtos.QrCodeResponse;
import com.flowledger.product.dto.ProductIdentificationDtos.ReorderImagesRequest;
import com.flowledger.product.service.ProductImageService;
import com.flowledger.product.service.QrCodeGeneratorService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products/{productId}")
public class ProductQrController {
    private final QrCodeGeneratorService qrService;
    private final ProductImageService imageService;

    public ProductQrController(QrCodeGeneratorService qrService, ProductImageService imageService) {
        this.qrService = qrService;
        this.imageService = imageService;
    }

    @GetMapping("/qr")
    @PreAuthorize("hasAuthority('BARCODE_READ') or hasAuthority('PRODUCT_READ')")
    public QrCodeResponse getQr(@PathVariable UUID productId) {
        return qrService.get(productId);
    }

    @PostMapping("/qr")
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public QrCodeResponse generateQr(
            @PathVariable UUID productId, @RequestBody(required = false) QrCodeRequest request) {
        return qrService.generate(productId, request);
    }

    @GetMapping("/images")
    @PreAuthorize("hasAuthority('BARCODE_READ') or hasAuthority('PRODUCT_READ')")
    public List<ImageResponse> listImages(@PathVariable UUID productId) {
        return imageService.list(productId);
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public ImageResponse uploadImage(
            @PathVariable UUID productId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean primary) {
        return imageService.upload(productId, file, primary);
    }

    @PutMapping("/images/reorder")
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public List<ImageResponse> reorderImages(
            @PathVariable UUID productId, @Valid @RequestBody ReorderImagesRequest request) {
        return imageService.reorder(productId, request);
    }

    @DeleteMapping("/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public void deleteImage(@PathVariable UUID productId, @PathVariable UUID imageId) {
        imageService.delete(productId, imageId);
    }
}
