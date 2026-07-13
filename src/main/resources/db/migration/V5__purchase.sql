-- Purchase documents: PO, GRN, purchase invoices, returns, debit notes

CREATE TABLE purchase_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    po_number           VARCHAR(100) NOT NULL,
    supplier_id         UUID NOT NULL REFERENCES suppliers(id),
    order_date          DATE NOT NULL,
    expected_delivery_date DATE,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    subtotal            NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_total      NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_total           NUMERIC(18,2) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(18,2) NOT NULL DEFAULT 0,
    terms_and_conditions TEXT,
    notes               TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_purchase_orders_number UNIQUE (organization_id, po_number)
);

CREATE TABLE purchase_order_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id   UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id),
    description         VARCHAR(500),
    hsn_sac_code        VARCHAR(20),
    quantity            NUMERIC(18,4) NOT NULL,
    unit_id             UUID REFERENCES units(id),
    rate                NUMERIC(18,4) NOT NULL,
    discount_percent    NUMERIC(8,4) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_rate            NUMERIC(8,4) NOT NULL DEFAULT 0,
    taxable_amount      NUMERIC(18,2) NOT NULL DEFAULT 0,
    cgst_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    sgst_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    igst_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_order          INT NOT NULL DEFAULT 0
);

CREATE TABLE goods_receipts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    grn_number          VARCHAR(100) NOT NULL,
    supplier_id         UUID NOT NULL REFERENCES suppliers(id),
    purchase_order_id   UUID REFERENCES purchase_orders(id),
    warehouse_id        UUID NOT NULL REFERENCES warehouses(id),
    receipt_date        DATE NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    inventory_posted    BOOLEAN NOT NULL DEFAULT FALSE,
    notes               TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_goods_receipts_number UNIQUE (organization_id, grn_number)
);

CREATE TABLE goods_receipt_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goods_receipt_id    UUID NOT NULL REFERENCES goods_receipts(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id),
    description         VARCHAR(500),
    quantity            NUMERIC(18,4) NOT NULL,
    unit_id             UUID REFERENCES units(id),
    batch_number        VARCHAR(100),
    expiry_date         DATE,
    line_order          INT NOT NULL DEFAULT 0
);

CREATE TABLE purchase_invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    invoice_number      VARCHAR(100) NOT NULL,
    supplier_invoice_number VARCHAR(100),
    invoice_date        DATE NOT NULL,
    due_date            DATE,
    supplier_id         UUID NOT NULL REFERENCES suppliers(id),
    purchase_order_id   UUID REFERENCES purchase_orders(id),
    goods_receipt_id    UUID REFERENCES goods_receipts(id),
    warehouse_id        UUID REFERENCES warehouses(id),
    place_of_supply     VARCHAR(100),
    supplier_gstin      VARCHAR(20),
    reverse_charge      BOOLEAN NOT NULL DEFAULT FALSE,
    tax_inclusive       BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    payment_status      VARCHAR(30) NOT NULL DEFAULT 'UNPAID',
    subtotal            NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_total      NUMERIC(18,2) NOT NULL DEFAULT 0,
    taxable_amount      NUMERIC(18,2) NOT NULL DEFAULT 0,
    cgst_total          NUMERIC(18,2) NOT NULL DEFAULT 0,
    sgst_total          NUMERIC(18,2) NOT NULL DEFAULT 0,
    igst_total          NUMERIC(18,2) NOT NULL DEFAULT 0,
    cess_total          NUMERIC(18,2) NOT NULL DEFAULT 0,
    shipping_charges    NUMERIC(18,2) NOT NULL DEFAULT 0,
    additional_charges  NUMERIC(18,2) NOT NULL DEFAULT 0,
    round_off           NUMERIC(18,2) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(18,2) NOT NULL DEFAULT 0,
    amount_paid         NUMERIC(18,2) NOT NULL DEFAULT 0,
    outstanding_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    notes               TEXT,
    terms_and_conditions TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_purchase_invoices_number UNIQUE (organization_id, invoice_number)
);

CREATE TABLE purchase_invoice_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_invoice_id UUID NOT NULL REFERENCES purchase_invoices(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id),
    description         VARCHAR(500),
    hsn_sac_code        VARCHAR(20),
    quantity            NUMERIC(18,4) NOT NULL,
    unit_id             UUID REFERENCES units(id),
    rate                NUMERIC(18,4) NOT NULL,
    discount_percent    NUMERIC(8,4) NOT NULL DEFAULT 0,
    discount_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_rate            NUMERIC(8,4) NOT NULL DEFAULT 0,
    taxable_amount      NUMERIC(18,2) NOT NULL DEFAULT 0,
    cgst_rate           NUMERIC(8,4) NOT NULL DEFAULT 0,
    sgst_rate           NUMERIC(8,4) NOT NULL DEFAULT 0,
    igst_rate           NUMERIC(8,4) NOT NULL DEFAULT 0,
    cgst_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    sgst_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    igst_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    cess_amount         NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_order          INT NOT NULL DEFAULT 0
);

CREATE TABLE purchase_returns (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    return_number       VARCHAR(100) NOT NULL,
    purchase_invoice_id UUID NOT NULL REFERENCES purchase_invoices(id),
    supplier_id         UUID NOT NULL REFERENCES suppliers(id),
    return_date         DATE NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    grand_total         NUMERIC(18,2) NOT NULL DEFAULT 0,
    inventory_posted    BOOLEAN NOT NULL DEFAULT FALSE,
    notes               TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_purchase_returns_number UNIQUE (organization_id, return_number)
);

CREATE TABLE purchase_return_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_return_id  UUID NOT NULL REFERENCES purchase_returns(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id),
    quantity            NUMERIC(18,4) NOT NULL,
    rate                NUMERIC(18,4) NOT NULL,
    line_total          NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_order          INT NOT NULL DEFAULT 0
);

CREATE TABLE debit_notes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    debit_note_number   VARCHAR(100) NOT NULL,
    purchase_return_id  UUID REFERENCES purchase_returns(id),
    purchase_invoice_id UUID REFERENCES purchase_invoices(id),
    supplier_id         UUID NOT NULL REFERENCES suppliers(id),
    debit_note_date     DATE NOT NULL,
    amount              NUMERIC(18,2) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_debit_notes_number UNIQUE (organization_id, debit_note_number)
);

CREATE INDEX idx_purchase_invoices_supplier ON purchase_invoices(organization_id, supplier_id);
CREATE INDEX idx_purchase_invoices_date ON purchase_invoices(organization_id, invoice_date);
