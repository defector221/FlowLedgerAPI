-- Epic 4.1: Fulfillment Engine

CREATE TABLE commerce_fulfillment_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    commerce_order_id   UUID NOT NULL UNIQUE REFERENCES commerce_orders(id) ON DELETE CASCADE,
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    store_id            UUID NOT NULL REFERENCES retail_stores(id),
    customer_id         UUID NOT NULL REFERENCES commerce_customers(id),
    fulfillment_type    VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    sub_status          VARCHAR(64),
    assigned_picker_id  UUID,
    accepted_at         TIMESTAMPTZ,
    ready_at            TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fulfillment_orders_store_status ON commerce_fulfillment_orders(store_id, status);
CREATE INDEX idx_fulfillment_orders_org_status ON commerce_fulfillment_orders(organization_id, status);

CREATE TABLE commerce_fulfillment_status_history (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fulfillment_order_id    UUID NOT NULL REFERENCES commerce_fulfillment_orders(id) ON DELETE CASCADE,
    from_status             VARCHAR(32),
    to_status               VARCHAR(32) NOT NULL,
    sub_status              VARCHAR(64),
    actor_id                UUID,
    note                    TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fulfillment_history_order ON commerce_fulfillment_status_history(fulfillment_order_id, created_at);

CREATE TABLE commerce_picking_tasks (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fulfillment_order_id    UUID NOT NULL REFERENCES commerce_fulfillment_orders(id) ON DELETE CASCADE,
    store_id                UUID NOT NULL REFERENCES retail_stores(id),
    picker_id               UUID,
    status                  VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    line_items              JSONB NOT NULL DEFAULT '[]',
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_picking_tasks_store_status ON commerce_picking_tasks(store_id, status);

CREATE TABLE commerce_packing_tasks (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fulfillment_order_id    UUID NOT NULL REFERENCES commerce_fulfillment_orders(id) ON DELETE CASCADE,
    picking_task_id         UUID REFERENCES commerce_picking_tasks(id),
    store_id                UUID NOT NULL REFERENCES retail_stores(id),
    packer_id               UUID,
    status                  VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    line_items              JSONB NOT NULL DEFAULT '[]',
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_packing_tasks_store_status ON commerce_packing_tasks(store_id, status);

CREATE TABLE commerce_pickup_sessions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fulfillment_order_id    UUID NOT NULL UNIQUE REFERENCES commerce_fulfillment_orders(id) ON DELETE CASCADE,
    customer_id             UUID NOT NULL REFERENCES commerce_customers(id),
    store_id                UUID NOT NULL REFERENCES retail_stores(id),
    status                  VARCHAR(32) NOT NULL DEFAULT 'WAITING',
    arrived_at              TIMESTAMPTZ,
    collected_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE commerce_collect_tokens (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fulfillment_order_id    UUID NOT NULL REFERENCES commerce_fulfillment_orders(id) ON DELETE CASCADE,
    token_hash              VARCHAR(128) NOT NULL UNIQUE,
    expires_at              TIMESTAMPTZ NOT NULL,
    verified_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_collect_tokens_fulfillment ON commerce_collect_tokens(fulfillment_order_id);

CREATE TABLE commerce_delivery_assignments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fulfillment_order_id    UUID NOT NULL UNIQUE REFERENCES commerce_fulfillment_orders(id) ON DELETE CASCADE,
    store_id                UUID NOT NULL REFERENCES retail_stores(id),
    driver_id               UUID,
    vehicle_id              UUID,
    shipment_id             UUID,
    status                  VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    assigned_at             TIMESTAMPTZ,
    dispatched_at           TIMESTAMPTZ,
    delivered_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_delivery_assignments_store_status ON commerce_delivery_assignments(store_id, status);
