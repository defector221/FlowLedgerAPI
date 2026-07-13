-- Sales documents: quotations, orders, challans, invoices, returns, credit notes

CREATE TABLE quotations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    quotation_number    VARCHAR(100) NOT NULL,
    customer_id         UUID NOT NULL REFERENCES customers(id),
    quotation_date      DATE NOT NULL,
    expiry_date         DATE,
    billing_address     TEXT,
    shipping_address    TEXT,
    place_of_supply     VARCHAR(100),
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    subtotal            NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_total      NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_total           NUMERIC(18,2) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(18,2) NOT NULL DEFAULT 0,
    terms_and_conditions TEXT,
    notes               TEXT,
    converted_to_order_id UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_quotations_number UNIQUE (organization_id, quotation_number)
);

CREATE TABLE quotation_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quotation_id    UUID NOT NULL REFERENCES quotations(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id),
    description     VARCHAR(500),
    hsn_sac_code    VARCHAR(20),
    quantity        NUMERIC(18,4) NOT NULL,
    unit_id         UUID REFERENCES units(id),
    rate            NUMERIC(18,4) NOT NULL,
    discount_percent NUMERIC(8,4) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_rate        NUMERIC(8,4) NOT NULL DEFAULT 0,
    taxable_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    cgst_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    sgst_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    igst_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_total      NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_order      INT NOT NULL DEFAULT 0
);

CREATE TABLE sales_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    order_number        VARCHAR(100) NOT NULL,
    customer_id         UUID NOT NULL REFERENCES customers(id),
    order_date          DATE NOT NULL,
    expected_delivery_date DATE,
    quotation_id        UUID REFERENCES quotations(id),
    billing_address     TEXT,
    shipping_address    TEXT,
    place_of_supply     VARCHAR(100),
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
    CONSTRAINT uq_sales_orders_number UNIQUE (organization_id, order_number)
);

CREATE TABLE sales_order_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_order_id  UUID NOT NULL REFERENCES sales_orders(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id),
    description     VARCHAR(500),
    hsn_sac_code    VARCHAR(20),
    quantity        NUMERIC(18,4) NOT NULL,
    unit_id         UUID REFERENCES units(id),
    rate            NUMERIC(18,4) NOT NULL,
    discount_percent NUMERIC(8,4) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_rate        NUMERIC(8,4) NOT NULL DEFAULT 0,
    taxable_amount  NUMERIC(18,2) NOT NULL DEFAULT 0,
    cgst_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    sgst_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    igst_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_total      NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_order      INT NOT NULL DEFAULT 0
);

CREATE TABLE delivery_challans (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    challan_number      VARCHAR(100) NOT NULL,
    customer_id         UUID NOT NULL REFERENCES customers(id),
    sales_order_id      UUID REFERENCES sales_orders(id),
    challan_date        DATE NOT NULL,
    warehouse_id        UUID REFERENCES warehouses(id),
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    notes               TEXT,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_delivery_challans_number UNIQUE (organization_id, challan_number)
);

CREATE TABLE delivery_challan_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_challan_id UUID NOT NULL REFERENCES delivery_challans(id) ON DELETE CASCADE,
    product_id          UUID NOT NULL REFERENCES products(id),
    description         VARCHAR(500),
    quantity            NUMERIC(18,4) NOT NULL,
    unit_id             UUID REFERENCES units(id),
    line_order          INT NOT NULL DEFAULT 0
);

CREATE TABLE sales_invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    invoice_number      VARCHAR(100) NOT NULL,
    invoice_date        DATE NOT NULL,
    due_date            DATE,
    customer_id         UUID NOT NULL REFERENCES customers(id),
    sales_order_id      UUID REFERENCES sales_orders(id),
    delivery_challan_id UUID REFERENCES delivery_challans(id),
    warehouse_id        UUID REFERENCES warehouses(id),
    billing_address     TEXT,
    shipping_address    TEXT,
    place_of_supply     VARCHAR(100),
    customer_gstin      VARCHAR(20),
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
    amount_in_words     VARCHAR(500),
    notes               TEXT,
    terms_and_conditions TEXT,
    inventory_posted    BOOLEAN NOT NULL DEFAULT FALSE,
    template_id         UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_sales_invoices_number UNIQUE (organization_id, invoice_number),
    CONSTRAINT chk_si_status CHECK (status IN ('DRAFT','CONFIRMED','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED'))
);

CREATE TABLE sales_invoice_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_invoice_id    UUID NOT NULL REFERENCES sales_invoices(id) ON DELETE CASCADE,
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

CREATE TABLE sales_returns (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    return_number       VARCHAR(100) NOT NULL,
    sales_invoice_id    UUID NOT NULL REFERENCES sales_invoices(id),
    customer_id         UUID NOT NULL REFERENCES customers(id),
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
    CONSTRAINT uq_sales_returns_number UNIQUE (organization_id, return_number)
);

CREATE TABLE sales_return_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sales_return_id UUID NOT NULL REFERENCES sales_returns(id) ON DELETE CASCADE,
    product_id      UUID NOT NULL REFERENCES products(id),
    quantity        NUMERIC(18,4) NOT NULL,
    rate            NUMERIC(18,4) NOT NULL,
    line_total      NUMERIC(18,2) NOT NULL DEFAULT 0,
    line_order      INT NOT NULL DEFAULT 0
);

CREATE TABLE credit_notes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    credit_note_number  VARCHAR(100) NOT NULL,
    sales_return_id     UUID REFERENCES sales_returns(id),
    sales_invoice_id    UUID REFERENCES sales_invoices(id),
    customer_id         UUID NOT NULL REFERENCES customers(id),
    credit_note_date    DATE NOT NULL,
    amount              NUMERIC(18,2) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by          UUID,
    updated_by          UUID,
    CONSTRAINT uq_credit_notes_number UNIQUE (organization_id, credit_note_number)
);

CREATE INDEX idx_sales_invoices_customer ON sales_invoices(organization_id, customer_id);
CREATE INDEX idx_sales_invoices_date ON sales_invoices(organization_id, invoice_date);
CREATE INDEX idx_sales_invoices_status ON sales_invoices(organization_id, status);
