CREATE TABLE supplier_catalog_items (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    product_id            UUID NOT NULL REFERENCES products(id),
    supplier_id           UUID NOT NULL REFERENCES suppliers(id),
    supplier_sku          VARCHAR,
    supplier_product_name VARCHAR,
    purchase_price        NUMERIC(19,4) NOT NULL,
    currency              VARCHAR(3) NOT NULL DEFAULT 'INR',
    moq                   NUMERIC(19,4),
    lead_time_days        INT,
    preferred             BOOLEAN NOT NULL DEFAULT FALSE,
    valid_from            DATE,
    valid_to              DATE,
    notes                 TEXT,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    deleted               BOOLEAN NOT NULL DEFAULT FALSE,
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by            UUID,
    updated_by            UUID
);

CREATE UNIQUE INDEX uq_supplier_catalog_org_product_supplier
    ON supplier_catalog_items (organization_id, product_id, supplier_id)
    WHERE deleted = FALSE;

CREATE UNIQUE INDEX uq_supplier_catalog_preferred_product
    ON supplier_catalog_items (organization_id, product_id)
    WHERE preferred = TRUE AND active = TRUE AND deleted = FALSE;

CREATE INDEX idx_supplier_catalog_org_supplier
    ON supplier_catalog_items (organization_id, supplier_id);

CREATE INDEX idx_supplier_catalog_org_product
    ON supplier_catalog_items (organization_id, product_id);

INSERT INTO supplier_catalog_items (
    organization_id,
    product_id,
    supplier_id,
    supplier_product_name,
    purchase_price,
    valid_from
)
SELECT DISTINCT ON (po.organization_id, poi.product_id, po.supplier_id)
    po.organization_id,
    poi.product_id,
    po.supplier_id,
    p.name,
    poi.rate,
    po.order_date
FROM purchase_order_items poi
JOIN purchase_orders po ON po.id = poi.purchase_order_id
JOIN products p ON p.id = poi.product_id AND p.organization_id = po.organization_id
JOIN suppliers s ON s.id = po.supplier_id AND s.organization_id = po.organization_id
ORDER BY
    po.organization_id,
    poi.product_id,
    po.supplier_id,
    po.order_date DESC,
    po.created_at DESC,
    poi.id DESC;

DO $$
BEGIN
    IF to_regclass('public.purchase_invoice_items') IS NOT NULL
       AND to_regclass('public.purchase_invoices') IS NOT NULL THEN
        EXECUTE $sql$
            INSERT INTO supplier_catalog_items (
                organization_id,
                product_id,
                supplier_id,
                supplier_product_name,
                purchase_price,
                valid_from
            )
            SELECT DISTINCT ON (pi.organization_id, pii.product_id, pi.supplier_id)
                pi.organization_id,
                pii.product_id,
                pi.supplier_id,
                p.name,
                pii.rate,
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
                pii.id DESC
        $sql$;
    END IF;
END
$$;
