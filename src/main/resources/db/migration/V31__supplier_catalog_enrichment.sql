-- Enrich supplier_catalog_items for existing orgs: re-sync from purchase history,
-- seed never-purchased products/services with up to 3 supplier links, and fill blank supplier_sku.

-- 1) Re-sync latest PO rates (idempotent)
INSERT INTO supplier_catalog_items (
    organization_id,
    product_id,
    supplier_id,
    supplier_sku,
    supplier_product_name,
    purchase_price,
    preferred,
    valid_from
)
SELECT DISTINCT ON (po.organization_id, poi.product_id, po.supplier_id)
    po.organization_id,
    poi.product_id,
    po.supplier_id,
    LEFT(CONCAT(COALESCE(NULLIF(TRIM(p.sku), ''), 'SKU'), '-', COALESCE(NULLIF(TRIM(s.supplier_code), ''), 'SUP')), 80),
    p.name,
    poi.rate,
    FALSE,
    po.order_date
FROM purchase_order_items poi
JOIN purchase_orders po ON po.id = poi.purchase_order_id
JOIN products p ON p.id = poi.product_id AND p.organization_id = po.organization_id
JOIN suppliers s ON s.id = po.supplier_id AND s.organization_id = po.organization_id
WHERE NOT EXISTS (
    SELECT 1
    FROM supplier_catalog_items sci
    WHERE sci.organization_id = po.organization_id
      AND sci.product_id = poi.product_id
      AND sci.supplier_id = po.supplier_id
      AND sci.deleted = FALSE
)
ORDER BY
    po.organization_id,
    poi.product_id,
    po.supplier_id,
    po.order_date DESC,
    po.created_at DESC,
    poi.id DESC;

-- 2) Re-sync from purchase invoices
INSERT INTO supplier_catalog_items (
    organization_id,
    product_id,
    supplier_id,
    supplier_sku,
    supplier_product_name,
    purchase_price,
    preferred,
    valid_from
)
SELECT DISTINCT ON (pi.organization_id, pii.product_id, pi.supplier_id)
    pi.organization_id,
    pii.product_id,
    pi.supplier_id,
    LEFT(CONCAT(COALESCE(NULLIF(TRIM(p.sku), ''), 'SKU'), '-', COALESCE(NULLIF(TRIM(s.supplier_code), ''), 'SUP')), 80),
    p.name,
    pii.rate,
    FALSE,
    pi.invoice_date
FROM purchase_invoice_items pii
JOIN purchase_invoices pi ON pi.id = pii.purchase_invoice_id
JOIN products p ON p.id = pii.product_id AND p.organization_id = pi.organization_id
JOIN suppliers s ON s.id = pi.supplier_id AND s.organization_id = pi.organization_id
WHERE NOT EXISTS (
    SELECT 1
    FROM supplier_catalog_items sci
    WHERE sci.organization_id = pi.organization_id
      AND sci.product_id = pii.product_id
      AND sci.supplier_id = pi.supplier_id
      AND sci.deleted = FALSE
)
ORDER BY
    pi.organization_id,
    pii.product_id,
    pi.supplier_id,
    pi.invoice_date DESC,
    pi.created_at DESC,
    pii.id DESC;

-- 3) For products/services with zero catalog rows, attach up to 3 suppliers
WITH products_without_catalog AS (
    SELECT p.id AS product_id, p.organization_id, p.name, p.sku, COALESCE(p.purchase_price, 0) AS purchase_price
    FROM products p
    WHERE p.active = TRUE
      AND NOT EXISTS (
          SELECT 1
          FROM supplier_catalog_items sci
          WHERE sci.organization_id = p.organization_id
            AND sci.product_id = p.id
            AND sci.deleted = FALSE
      )
),
org_trading_suppliers AS (
    SELECT DISTINCT organization_id, supplier_id
    FROM (
        SELECT organization_id, supplier_id FROM purchase_orders
        UNION
        SELECT organization_id, supplier_id FROM purchase_invoices
    ) t
),
candidate_suppliers AS (
    SELECT
        pwc.product_id,
        pwc.organization_id,
        pwc.name,
        pwc.sku,
        pwc.purchase_price,
        s.id AS supplier_id,
        s.supplier_code,
        ROW_NUMBER() OVER (
            PARTITION BY pwc.organization_id, pwc.product_id
            ORDER BY
                CASE WHEN ots.supplier_id IS NOT NULL THEN 0 ELSE 1 END,
                s.supplier_code,
                s.id
        ) AS rn
    FROM products_without_catalog pwc
    JOIN suppliers s
      ON s.organization_id = pwc.organization_id
     AND COALESCE(s.archived, FALSE) = FALSE
    LEFT JOIN org_trading_suppliers ots
      ON ots.organization_id = pwc.organization_id
     AND ots.supplier_id = s.id
)
INSERT INTO supplier_catalog_items (
    organization_id,
    product_id,
    supplier_id,
    supplier_sku,
    supplier_product_name,
    purchase_price,
    preferred,
    active
)
SELECT
    cs.organization_id,
    cs.product_id,
    cs.supplier_id,
    LEFT(CONCAT(COALESCE(NULLIF(TRIM(cs.sku), ''), 'SKU'), '-', COALESCE(NULLIF(TRIM(cs.supplier_code), ''), 'SUP'), '-', cs.rn::text), 80),
    cs.name,
    ROUND(
        CASE cs.rn
            WHEN 1 THEN cs.purchase_price
            WHEN 2 THEN cs.purchase_price * 1.02
            ELSE cs.purchase_price * 1.05
        END,
        4
    ),
    cs.rn = 1,
    TRUE
FROM candidate_suppliers cs
WHERE cs.rn <= 3;

-- Prefer first catalog row per product when none preferred yet
UPDATE supplier_catalog_items sci
SET preferred = TRUE,
    updated_at = NOW()
FROM (
    SELECT DISTINCT ON (organization_id, product_id) id
    FROM supplier_catalog_items
    WHERE deleted = FALSE
      AND active = TRUE
      AND (organization_id, product_id) IN (
          SELECT organization_id, product_id
          FROM supplier_catalog_items
          WHERE deleted = FALSE AND active = TRUE
          GROUP BY organization_id, product_id
          HAVING BOOL_OR(preferred) = FALSE
      )
    ORDER BY organization_id, product_id, created_at ASC, id ASC
) first_row
WHERE sci.id = first_row.id
  AND sci.preferred = FALSE;

-- 4) Fill blank supplier_sku on existing rows
UPDATE supplier_catalog_items sci
SET supplier_sku = LEFT(
        CONCAT(
            COALESCE(NULLIF(TRIM(p.sku), ''), 'SKU'),
            '-',
            COALESCE(NULLIF(TRIM(s.supplier_code), ''), 'SUP')
        ),
        80
    ),
    updated_at = NOW()
FROM products p, suppliers s
WHERE sci.product_id = p.id
  AND sci.supplier_id = s.id
  AND sci.deleted = FALSE
  AND (sci.supplier_sku IS NULL OR TRIM(sci.supplier_sku) = '');
