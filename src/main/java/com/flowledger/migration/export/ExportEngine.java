package com.flowledger.migration.export;

import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.migration.domain.ExportFormat;
import com.flowledger.migration.domain.ExportJobStatus;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.migration.dto.MigrationDtos.ExportJobResponse;
import com.flowledger.migration.dto.MigrationDtos.ExportRequest;
import com.flowledger.migration.entity.ExportJob;
import com.flowledger.migration.mapping.ModuleFieldCatalog;
import com.flowledger.migration.repository.ExportJobRepository;
import com.flowledger.organization.repository.BranchRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.retail.repository.RetailProductBarcodeRepository;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.storage.BytesMultipartFile;
import com.flowledger.storage.StorageService;
import com.flowledger.supplier.repository.SupplierRepository;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportEngine {
    private final ExportJobRepository jobs;
    private final StorageService storage;
    private final ModuleFieldCatalog catalog;
    private final BranchRepository branches;
    private final RetailStoreRepository stores;
    private final WarehouseRepository warehouses;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final RetailProductBarcodeRepository barcodes;
    private final AccountRepository accounts;
    private final SalesInvoiceRepository salesInvoices;

    public ExportEngine(
            ExportJobRepository jobs,
            StorageService storage,
            ModuleFieldCatalog catalog,
            BranchRepository branches,
            RetailStoreRepository stores,
            WarehouseRepository warehouses,
            CustomerRepository customers,
            SupplierRepository suppliers,
            ProductRepository products,
            RetailProductBarcodeRepository barcodes,
            AccountRepository accounts,
            SalesInvoiceRepository salesInvoices) {
        this.jobs = jobs;
        this.storage = storage;
        this.catalog = catalog;
        this.branches = branches;
        this.stores = stores;
        this.warehouses = warehouses;
        this.customers = customers;
        this.suppliers = suppliers;
        this.products = products;
        this.barcodes = barcodes;
        this.accounts = accounts;
        this.salesInvoices = salesInvoices;
    }

    @Transactional
    public ExportJobResponse export(ExportRequest request) {
        UUID org = TenantContext.getOrganizationId();
        ExportJob job = new ExportJob();
        job.setOrganizationId(org);
        job.setModule(request.module());
        job.setFormat(request.format());
        job.setStatus(ExportJobStatus.RUNNING);
        job.setStartedAt(OffsetDateTime.now());
        TenantContext.userId().ifPresent(job::setCreatedBy);
        job = jobs.save(job);

        try {
            List<String> headers = catalog.fieldsFor(request.module());
            List<List<String>> dataRows = loadRows(org, request.module(), headers);
            byte[] bytes;
            String fileName;
            String contentType;
            if (request.format() == ExportFormat.XLSX) {
                bytes = toXlsx(headers, dataRows);
                fileName = request.module().name().toLowerCase() + ".xlsx";
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else {
                bytes = toCsv(headers, dataRows);
                fileName = request.module().name().toLowerCase() + ".csv";
                contentType = "text/csv";
            }
            String key = "migration/" + org + "/exports/" + job.getId() + "/" + fileName;
            storage.store(key, new BytesMultipartFile(fileName, contentType, bytes));
            job.setFileObjectKey(key);
            job.setFileName(fileName);
            job.setTotalRows(dataRows.size());
            job.setStatus(ExportJobStatus.COMPLETED);
            job.setCompletedAt(OffsetDateTime.now());
            jobs.save(job);
            return toResponse(job);
        } catch (Exception e) {
            job.setStatus(ExportJobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(OffsetDateTime.now());
            jobs.save(job);
            throw e instanceof RuntimeException re ? re : new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public ExportJobResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public byte[] download(UUID id) {
        ExportJob job = require(id);
        if (job.getFileObjectKey() == null) {
            throw new ResourceNotFoundException("Export file not ready");
        }
        try (var in = storage.get(job.getFileObjectKey())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to download export", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] template(ImportModule module) {
        List<String> headers = catalog.fieldsFor(module);
        return toXlsx(headers, List.of());
    }

    private ExportJob require(UUID id) {
        return jobs.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Export job not found"));
    }

    private ExportJobResponse toResponse(ExportJob job) {
        String url = job.getFileObjectKey() == null
                ? null
                : "/api/v1/migration/export/" + job.getId() + "/download";
        return new ExportJobResponse(
                job.getId(),
                job.getModule().name(),
                job.getFormat().name(),
                job.getStatus(),
                job.getFileName(),
                job.getTotalRows(),
                job.getErrorMessage(),
                url,
                job.getStartedAt(),
                job.getCreatedAt(),
                job.getCompletedAt());
    }

    private List<List<String>> loadRows(UUID org, ImportModule module, List<String> headers) {
        return switch (module) {
            case BRANCH -> branches.findByOrganizationIdOrderByNameAsc(org).stream()
                    .map(b -> values(headers, MapBuilder.of()
                            .put("branchCode", b.getCode())
                            .put("branchName", b.getName())
                            .put("gstNumber", b.getGstNumber())
                            .put("pan", b.getPan())
                            .put("city", b.getCity())
                            .put("state", b.getState())
                            .put("phone", b.getPhone())
                            .put("email", b.getEmail())
                            .build()))
                    .toList();
            case STORE -> stores.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org).stream()
                    .map(s -> values(headers, MapBuilder.of()
                            .put("storeCode", s.getCode())
                            .put("storeName", s.getName())
                            .put("city", s.getCity())
                            .put("state", s.getState())
                            .put("phone", s.getPhone())
                            .put("email", s.getEmail())
                            .put("status", s.getStatus())
                            .build()))
                    .toList();
            case WAREHOUSE -> warehouses.findByOrganizationId(org).stream()
                    .map(w -> values(headers, MapBuilder.of()
                            .put("warehouseCode", w.getWarehouseCode())
                            .put("warehouseName", w.getWarehouseName())
                            .put("warehouseType", w.getWarehouseType() == null ? null : w.getWarehouseType().name())
                            .put("address", w.getAddress())
                            .put("phone", w.getPhone())
                            .build()))
                    .toList();
            case CUSTOMER -> customers.findByOrganizationId(org).stream()
                    .map(c -> values(headers, MapBuilder.of()
                            .put("customerCode", c.getCustomerCode())
                            .put("customerName", c.getCustomerName())
                            .put("gstin", c.getGstin())
                            .put("pan", c.getPan())
                            .put("email", c.getEmail())
                            .put("phone", c.getPhone())
                            .put("city", c.getCity())
                            .put("state", c.getState())
                            .build()))
                    .toList();
            case SUPPLIER -> suppliers.findByOrganizationId(org).stream()
                    .map(s -> values(headers, MapBuilder.of()
                            .put("supplierCode", s.getSupplierCode())
                            .put("supplierName", s.getSupplierName())
                            .put("gstin", s.getGstin())
                            .put("pan", s.getPan())
                            .put("email", s.getEmail())
                            .put("phone", s.getPhone())
                            .put("city", s.getCity())
                            .put("state", s.getState())
                            .build()))
                    .toList();
            case PRODUCT -> products.findByOrganizationId(org).stream()
                    .map(p -> values(headers, MapBuilder.of()
                            .put("productCode", p.getSku())
                            .put("productName", p.getName())
                            .put("hsnSacCode", p.getHsnSacCode())
                            .put("barcode", p.getBarcode())
                            .put("mrp", str(p.getMrp()))
                            .put("sellingPrice", str(p.getSellingPrice()))
                            .put("purchasePrice", str(p.getPurchasePrice()))
                            .build()))
                    .toList();
            case BARCODE -> products.findByOrganizationId(org).stream()
                    .flatMap(p -> barcodes
                            .findByOrganizationIdAndProductIdAndDeletedAtIsNullOrderByPrimaryDescCreatedAtAsc(
                                    org, p.getId())
                            .stream()
                            .map(b -> values(headers, MapBuilder.of()
                                    .put("productCode", p.getSku())
                                    .put("barcode", b.getBarcode())
                                    .put("barcodeType", b.getBarcodeType())
                                    .put("status", b.getStatus())
                                    .build())))
                    .toList();
            case COA -> accounts.findByOrganizationIdOrderByAccountCodeAsc(org).stream()
                    .map(a -> values(headers, MapBuilder.of()
                            .put("accountCode", a.getAccountCode())
                            .put("accountName", a.getAccountName())
                            .put("accountType", a.getAccountType() == null ? null : a.getAccountType().name())
                            .put("openingDebit", str(a.getOpeningDebit()))
                            .put("openingCredit", str(a.getOpeningCredit()))
                            .build()))
                    .toList();
            case SALES_INVOICE -> salesInvoices.findByOrganizationId(org, PageRequest.of(0, 5000)).stream()
                    .map(i -> values(headers, MapBuilder.of()
                            .put("invoiceNumber", i.getInvoiceNumber())
                            .put("invoiceDate", i.getInvoiceDate() == null ? null : i.getInvoiceDate().toString())
                            .put("grandTotal", str(i.getGrandTotal()))
                            .build()))
                    .toList();
            default -> List.of();
        };
    }

    private static List<String> values(List<String> headers, java.util.Map<String, String> map) {
        List<String> row = new ArrayList<>(headers.size());
        for (String h : headers) {
            row.add(map.getOrDefault(h, ""));
        }
        return row;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static byte[] toCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(headers.stream().map(ExportEngine::csvEscape).collect(Collectors.joining(","))).append('\n');
        for (List<String> row : rows) {
            sb.append(row.stream().map(ExportEngine::csvEscape).collect(Collectors.joining(","))).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvEscape(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static byte[] toXlsx(List<String> headers, List<List<String>> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("data");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
            }
            int r = 1;
            for (List<String> data : rows) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < data.size(); c++) {
                    row.createCell(c).setCellValue(data.get(c) == null ? "" : data.get(c));
                }
            }
            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Excel export", e);
        }
    }

    /** Tiny fluent map for export row building. */
    private static final class MapBuilder {
        private final java.util.Map<String, String> map = new java.util.LinkedHashMap<>();

        static MapBuilder of() {
            return new MapBuilder();
        }

        MapBuilder put(String k, String v) {
            map.put(k, v == null ? "" : v);
            return this;
        }

        java.util.Map<String, String> build() {
            return map;
        }
    }
}
