package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.product.dto.ProductIdentificationDtos.QrCodeRequest;
import com.flowledger.product.dto.ProductIdentificationDtos.QrCodeResponse;
import com.flowledger.product.entity.Product;
import com.flowledger.product.entity.ProductQrCode;
import com.flowledger.product.repository.ProductQrCodeRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.storage.BytesMultipartFile;
import com.flowledger.storage.StorageService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class QrCodeGeneratorService extends OrganizationScopedService {
    private final ProductRepository products;
    private final ProductQrCodeRepository qrCodes;
    private final StorageService storage;
    private final PublicProductService publicProducts;

    public QrCodeGeneratorService(
            ProductRepository products,
            ProductQrCodeRepository qrCodes,
            StorageService storage,
            PublicProductService publicProducts) {
        this.products = products;
        this.qrCodes = qrCodes;
        this.storage = storage;
        this.publicProducts = publicProducts;
    }

    @Transactional(readOnly = true)
    public QrCodeResponse get(UUID productId) {
        Product product = requireProduct(productId);
        return qrCodes.findByOrganizationIdAndProductId(orgId(), productId)
                .map(row -> toResponse(row, product))
                .orElseGet(() -> toResponse(null, product));
    }

    public QrCodeResponse generate(UUID productId, QrCodeRequest request) {
        Product product = requireProduct(productId);
        String template = request == null || request.payloadTemplate() == null || request.payloadTemplate().isBlank()
                ? "{{productUrl}}"
                : request.payloadTemplate();
        String resolved = resolveTemplate(template, product);
        byte[] png = encodePng(resolved);
        String pngKey = objectKey(productId, "png");
        storage.store(pngKey, new BytesMultipartFile("qr.png", "image/png", png));
        ProductQrCode row = qrCodes.findByOrganizationIdAndProductId(orgId(), productId).orElseGet(ProductQrCode::new);
        if (row.getId() == null) {
            row.setOrganizationId(orgId());
            row.setProductId(productId);
            TenantContext.userId().ifPresent(row::setCreatedBy);
        }
        row.setPayloadTemplate(template);
        row.setPayloadResolved(resolved);
        row.setFormat("PNG");
        row.setObjectKeyPng(pngKey);
        TenantContext.userId().ifPresent(row::setUpdatedBy);
        return toResponse(qrCodes.save(row), product);
    }

    private String resolveTemplate(String template, Product product) {
        String productUrl = publicProducts.publishedProductUrl(product.getId());
        String warrantyUrl = productUrl + "/warranty";
        return template
                .replace("{{sku}}", product.getSku() == null ? "" : product.getSku())
                .replace("{{productName}}", product.getName() == null ? "" : product.getName())
                .replace("{{barcode}}", product.getBarcode() == null ? "" : product.getBarcode())
                .replace("{{productUrl}}", productUrl)
                .replace("{{warrantyUrl}}", warrantyUrl);
    }

    private byte[] encodePng(String payload) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate QR code");
        }
    }

    private String objectKey(UUID productId, String ext) {
        return "products/" + orgId() + "/" + productId + "/qr." + ext;
    }

    private Product requireProduct(UUID productId) {
        return required(products.findByIdAndOrganizationId(productId, orgId()), "Product");
    }

    private QrCodeResponse toResponse(ProductQrCode row, Product product) {
        if (row == null) {
            return new QrCodeResponse(
                    null,
                    product.getId(),
                    "{{productUrl}}",
                    resolveTemplate("{{productUrl}}", product),
                    "PNG",
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        return new QrCodeResponse(
                row.getId(),
                row.getProductId(),
                row.getPayloadTemplate(),
                row.getPayloadResolved(),
                row.getFormat(),
                row.getObjectKeyPng(),
                row.getObjectKeySvg(),
                presign(row.getObjectKeyPng()),
                presign(row.getObjectKeySvg()),
                row.getUpdatedAt());
    }

    private String presign(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return storage.getPresignedUrl(key, Duration.ofHours(1));
    }
}
