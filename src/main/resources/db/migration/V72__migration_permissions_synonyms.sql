-- Migration permissions and synonym dictionary

INSERT INTO permissions (id, code, name, module)
SELECT gen_random_uuid(), v.code, v.name, v.module
FROM (VALUES
    ('MIGRATION_READ', 'View migration jobs and history', 'MIGRATION'),
    ('MIGRATION_WRITE', 'Upload, map, validate, and import data', 'MIGRATION'),
    ('MIGRATION_EXPORT', 'Export masters and transactions', 'MIGRATION')
) AS v(code, name, module)
WHERE NOT EXISTS (SELECT 1 FROM permissions p WHERE p.code = v.code);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('ORGANIZATION_ADMIN', 'ACCOUNTANT')
  AND p.code IN ('MIGRATION_READ', 'MIGRATION_WRITE', 'MIGRATION_EXPORT')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('INVENTORY_MANAGER', 'SALES_MANAGER', 'PURCHASE_MANAGER')
  AND p.code IN ('MIGRATION_READ', 'MIGRATION_EXPORT')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Common column synonyms (module NULL = global)
INSERT INTO import_synonyms (id, synonym, target_field, module)
SELECT gen_random_uuid(), v.synonym, v.target_field, v.module
FROM (VALUES
    -- Invoice / document numbers
    ('bill no', 'invoiceNumber', NULL),
    ('bill number', 'invoiceNumber', NULL),
    ('invoice no', 'invoiceNumber', NULL),
    ('invoice number', 'invoiceNumber', NULL),
    ('invoice#', 'invoiceNumber', NULL),
    ('voucher no', 'invoiceNumber', NULL),
    ('voucher number', 'invoiceNumber', NULL),
    ('doc no', 'invoiceNumber', NULL),
    ('document number', 'invoiceNumber', NULL),
    ('bill date', 'invoiceDate', NULL),
    ('invoice date', 'invoiceDate', NULL),
    ('voucher date', 'invoiceDate', NULL),
    -- Party
    ('customer', 'customerName', NULL),
    ('customer name', 'customerName', NULL),
    ('party', 'customerName', NULL),
    ('party name', 'customerName', NULL),
    ('client', 'customerName', NULL),
    ('buyer', 'customerName', NULL),
    ('customer code', 'customerCode', NULL),
    ('party code', 'customerCode', NULL),
    ('supplier', 'supplierName', NULL),
    ('supplier name', 'supplierName', NULL),
    ('vendor', 'supplierName', NULL),
    ('vendor name', 'supplierName', NULL),
    ('supplier code', 'supplierCode', NULL),
    -- Product
    ('sku', 'productCode', NULL),
    ('item code', 'productCode', NULL),
    ('product code', 'productCode', NULL),
    ('item name', 'productName', NULL),
    ('product name', 'productName', NULL),
    ('product', 'productName', NULL),
    ('item', 'productName', NULL),
    ('barcode', 'barcode', NULL),
    ('ean', 'barcode', NULL),
    ('hsn', 'hsnSacCode', NULL),
    ('hsn code', 'hsnSacCode', NULL),
    ('hsn/sac', 'hsnSacCode', NULL),
    ('unit', 'unitCode', NULL),
    ('uom', 'unitCode', NULL),
    ('qty', 'quantity', NULL),
    ('quantity', 'quantity', NULL),
    ('rate', 'rate', NULL),
    ('price', 'rate', NULL),
    ('mrp', 'mrp', NULL),
    ('selling price', 'sellingPrice', NULL),
    ('purchase price', 'purchasePrice', NULL),
    ('cost', 'costPrice', NULL),
    ('tax %', 'taxRate', NULL),
    ('tax rate', 'taxRate', NULL),
    ('gst %', 'taxRate', NULL),
    ('gstin', 'gstin', NULL),
    ('gst no', 'gstin', NULL),
    ('pan', 'pan', NULL),
    ('phone', 'phone', NULL),
    ('mobile', 'phone', NULL),
    ('email', 'email', NULL),
    ('address', 'address', NULL),
    ('city', 'city', NULL),
    ('state', 'state', NULL),
    ('pincode', 'postalCode', NULL),
    ('postal code', 'postalCode', NULL),
    -- Location
    ('branch', 'branchCode', NULL),
    ('branch code', 'branchCode', NULL),
    ('branch name', 'branchName', NULL),
    ('store', 'storeCode', NULL),
    ('store code', 'storeCode', NULL),
    ('store name', 'storeName', NULL),
    ('warehouse', 'warehouseCode', NULL),
    ('warehouse code', 'warehouseCode', NULL),
    ('warehouse name', 'warehouseName', NULL),
    ('godown', 'warehouseCode', NULL),
    -- Stock
    ('opening stock', 'quantity', 'OPENING_STOCK'),
    ('stock qty', 'quantity', 'OPENING_STOCK'),
    ('batch', 'batchNumber', NULL),
    ('batch no', 'batchNumber', NULL),
    ('batch number', 'batchNumber', NULL),
    ('expiry', 'expiryDate', NULL),
    ('expiry date', 'expiryDate', NULL),
    -- Finance
    ('account code', 'accountCode', NULL),
    ('account name', 'accountName', NULL),
    ('ledger', 'accountName', NULL),
    ('debit', 'debit', NULL),
    ('credit', 'credit', NULL),
    ('opening balance', 'openingBalance', NULL),
    ('amount', 'amount', NULL),
    -- POS
    ('terminal', 'terminalCode', NULL),
    ('terminal code', 'terminalCode', NULL),
    ('bill amount', 'grandTotal', NULL),
    ('grand total', 'grandTotal', NULL),
    ('net amount', 'grandTotal', NULL)
) AS v(synonym, target_field, module)
WHERE NOT EXISTS (
    SELECT 1 FROM import_synonyms s
    WHERE lower(s.synonym) = lower(v.synonym)
      AND COALESCE(s.module, '*') = COALESCE(v.module, '*')
);
