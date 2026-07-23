-- POS sales linked to core sales invoices

CREATE TABLE pos_sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    store_id UUID NOT NULL REFERENCES retail_stores(id),
    counter_id UUID REFERENCES retail_cash_counters(id),
    terminal_id UUID REFERENCES retail_terminals(id),
    shift_id UUID REFERENCES retail_shifts(id),
    cashier_id UUID REFERENCES retail_cashiers(id),
    customer_id UUID,
    sales_invoice_id UUID,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    receipt_type VARCHAR(30) NOT NULL DEFAULT 'POS_RECEIPT',
    bill_number VARCHAR(50),
    subtotal NUMERIC(18, 2) NOT NULL DEFAULT 0,
    discount_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    tax_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    grand_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    held_label VARCHAR(100),
    notes TEXT,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE INDEX idx_pos_sales_org_status ON pos_sales(organization_id, status);
CREATE INDEX idx_pos_sales_invoice ON pos_sales(sales_invoice_id);

CREATE TABLE pos_sale_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    pos_sale_id UUID NOT NULL REFERENCES pos_sales(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    variant_id UUID,
    description VARCHAR(500),
    barcode VARCHAR(100),
    quantity NUMERIC(18, 4) NOT NULL,
    rate NUMERIC(18, 4) NOT NULL,
    discount_percent NUMERIC(8, 4) NOT NULL DEFAULT 0,
    tax_rate NUMERIC(8, 4) NOT NULL DEFAULT 0,
    line_total NUMERIC(18, 2) NOT NULL DEFAULT 0,
    line_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE pos_sale_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    pos_sale_id UUID NOT NULL REFERENCES pos_sales(id) ON DELETE CASCADE,
    payment_mode VARCHAR(30) NOT NULL,
    amount NUMERIC(18, 2) NOT NULL,
    payment_id UUID,
    reference VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);
