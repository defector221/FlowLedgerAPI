package com.flowledger.barcode.service;

import com.flowledger.retail.repository.RetailProductBarcodeRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.CRC32;
import org.springframework.stereotype.Service;

@Service
public class BarcodeGeneratorService {
    private static final String DEFAULT_PREFIX = "890";

    private final RetailProductBarcodeRepository barcodes;

    public BarcodeGeneratorService(RetailProductBarcodeRepository barcodes) {
        this.barcodes = barcodes;
    }

    /**
     * Generates a unique EAN-13 style barcode for the product using SKU and id as entropy.
     */
    public String generateEan13(UUID organizationId, UUID productId, String sku) {
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = buildCandidate(organizationId, productId, sku, attempt);
            if (!barcodes.existsActiveByOrganizationIdAndBarcode(organizationId, candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique barcode for product " + productId);
    }

    private String buildCandidate(UUID organizationId, UUID productId, String sku, int attempt) {
        String seed = (sku == null ? "" : sku.trim().toUpperCase(Locale.ROOT))
                + ":"
                + productId
                + ":"
                + organizationId
                + ":"
                + attempt;
        CRC32 crc = new CRC32();
        crc.update(seed.getBytes(StandardCharsets.UTF_8));
        long value = crc.getValue() % 1_000_000_000_000L;
        String body = DEFAULT_PREFIX + String.format("%09d", value);
        if (body.length() > 12) {
            body = body.substring(0, 12);
        } else if (body.length() < 12) {
            body = String.format("%12s", body).replace(' ', '0');
        }
        return body + checkDigit(body);
    }

    static char checkDigit(String twelveDigits) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = Character.digit(twelveDigits.charAt(i), 10);
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        int mod = sum % 10;
        return (char) ('0' + ((10 - mod) % 10));
    }
}
