package com.flowledger.pdf.controller;

import com.flowledger.pdf.InvoicePdfService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentPdfController {
    private final InvoicePdfService pdf;

    public DocumentPdfController(InvoicePdfService pdf) {
        this.pdf = pdf;
    }

    @GetMapping("/{type}/{id}/pdf")
    public ResponseEntity<byte[]> download(@PathVariable String type, @PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + type.toLowerCase(Locale.ROOT) + "-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.renderDocument(type, id));
    }

    @GetMapping("/{type}/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable String type, @PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(pdf.renderDocument(type, id));
    }
}
