-- Epic 4.2: Scheduled slots and capacity

CREATE TABLE commerce_delivery_slots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    store_id            UUID NOT NULL REFERENCES retail_stores(id),
    slot_date           DATE NOT NULL,
    start_time          TIME NOT NULL,
    end_time            TIME NOT NULL,
    max_orders          INT NOT NULL DEFAULT 10,
    booked_count        INT NOT NULL DEFAULT 0,
    prep_minutes        INT NOT NULL DEFAULT 30,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_delivery_slot UNIQUE (store_id, slot_date, start_time)
);

CREATE INDEX idx_delivery_slots_store_date ON commerce_delivery_slots(store_id, slot_date);

CREATE TABLE commerce_pickup_slots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    store_id            UUID NOT NULL REFERENCES retail_stores(id),
    slot_date           DATE NOT NULL,
    start_time          TIME NOT NULL,
    end_time            TIME NOT NULL,
    max_orders          INT NOT NULL DEFAULT 20,
    booked_count        INT NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_pickup_slot UNIQUE (store_id, slot_date, start_time)
);

CREATE INDEX idx_pickup_slots_store_date ON commerce_pickup_slots(store_id, slot_date);

CREATE TABLE commerce_slot_bookings (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    commerce_order_id       UUID REFERENCES commerce_orders(id) ON DELETE SET NULL,
    fulfillment_order_id    UUID REFERENCES commerce_fulfillment_orders(id) ON DELETE SET NULL,
    slot_type               VARCHAR(32) NOT NULL,
    delivery_slot_id        UUID REFERENCES commerce_delivery_slots(id) ON DELETE SET NULL,
    pickup_slot_id          UUID REFERENCES commerce_pickup_slots(id) ON DELETE SET NULL,
    booked_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE commerce_store_fulfillment_capacity (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    store_id            UUID NOT NULL UNIQUE REFERENCES retail_stores(id),
    daily_order_limit   INT,
    default_prep_minutes INT NOT NULL DEFAULT 30,
    holiday_dates       JSONB NOT NULL DEFAULT '[]',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
