-- Multi-leg extension: additive columns, GPS history, leg documents, Leg-1 backfill.
-- Does not rename existing shipment statuses.

ALTER TABLE shipments
    ADD COLUMN IF NOT EXISTS priority VARCHAR(30),
    ADD COLUMN IF NOT EXISTS total_distance NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS fuel_charges_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS toll_charges_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS other_charges_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS grand_total NUMERIC(19,4) NOT NULL DEFAULT 0;

ALTER TABLE shipment_legs
    ADD COLUMN IF NOT EXISTS organization_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
    ADD COLUMN IF NOT EXISTS transport_mode VARCHAR(30),
    ADD COLUMN IF NOT EXISTS origin_location VARCHAR(500),
    ADD COLUMN IF NOT EXISTS destination_location VARCHAR(500),
    ADD COLUMN IF NOT EXISTS waypoints_json JSONB,
    ADD COLUMN IF NOT EXISTS estimated_distance NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS actual_distance NUMERIC(19,4),
    ADD COLUMN IF NOT EXISTS estimated_duration_minutes INT,
    ADD COLUMN IF NOT EXISTS actual_duration_minutes INT,
    ADD COLUMN IF NOT EXISTS freight_cost NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS fuel_cost NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS toll_cost NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS other_charges NUMERIC(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS current_latitude NUMERIC(12,8),
    ADD COLUMN IF NOT EXISTS current_longitude NUMERIC(12,8),
    ADD COLUMN IF NOT EXISTS location_updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS current_speed NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS vehicle_heading NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS gps_provider VARCHAR(100),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS updated_by UUID;

UPDATE shipment_legs leg
SET organization_id = s.organization_id,
    transport_mode = COALESCE(leg.transport_mode, s.transport_mode),
    status = COALESCE(NULLIF(leg.status, ''), 'PLANNED')
FROM shipments s
WHERE leg.shipment_id = s.id
  AND leg.organization_id IS NULL;

ALTER TABLE shipment_legs
    ALTER COLUMN organization_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_shipment_leg_status'
    ) THEN
        ALTER TABLE shipment_legs
            ADD CONSTRAINT chk_shipment_leg_status CHECK (status IN (
                'PLANNED', 'READY', 'DISPATCHED', 'IN_TRANSIT', 'ARRIVED', 'COMPLETED', 'CANCELLED'
            ));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_shipment_leg_mode'
    ) THEN
        ALTER TABLE shipment_legs
            ADD CONSTRAINT chk_shipment_leg_mode CHECK (
                transport_mode IS NULL OR transport_mode IN (
                    'ROAD', 'RAIL', 'AIR', 'SEA', 'COURIER', 'CUSTOMER_PICKUP', 'INTERNAL_VEHICLE'
                )
            );
    END IF;
END $$;

-- Backfill Leg 1 for shipments that have none
INSERT INTO shipment_legs (
    id, shipment_id, organization_id, sequence_no, transport_company_id,
    transport_mode, status, expected_departure, expected_arrival, remarks,
    freight_cost, created_at, updated_at, deleted, version
)
SELECT
    gen_random_uuid(),
    s.id,
    s.organization_id,
    1,
    s.transport_company_id,
    s.transport_mode,
    CASE
        WHEN s.status IN ('DISPATCHED', 'PARTIALLY_DISPATCHED', 'IN_TRANSIT') THEN 'IN_TRANSIT'
        WHEN s.status IN ('DELIVERED', 'CLOSED') THEN 'COMPLETED'
        WHEN s.status = 'CANCELLED' THEN 'CANCELLED'
        WHEN s.status IN ('ASSIGNED', 'LOADING', 'LOADED', 'APPROVED') THEN 'READY'
        ELSE 'PLANNED'
    END,
    s.expected_dispatch_date,
    s.expected_delivery_date,
    s.remarks,
    COALESCE(s.freight_charges, 0),
    COALESCE(s.created_at, NOW()),
    COALESCE(s.updated_at, NOW()),
    FALSE,
    0
FROM shipments s
WHERE s.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM shipment_legs l WHERE l.shipment_id = s.id);

-- Sync shipment cost totals from legs (single pass)
UPDATE shipments s
SET freight_charges = COALESCE(agg.freight, s.freight_charges),
    fuel_charges_total = COALESCE(agg.fuel, 0),
    toll_charges_total = COALESCE(agg.toll, 0),
    other_charges_total = COALESCE(agg.other, 0),
    grand_total = COALESCE(agg.freight, 0) + COALESCE(agg.fuel, 0) + COALESCE(agg.toll, 0) + COALESCE(agg.other, 0)
FROM (
    SELECT shipment_id,
           SUM(freight_cost) AS freight,
           SUM(fuel_cost) AS fuel,
           SUM(toll_cost) AS toll,
           SUM(other_charges) AS other
    FROM shipment_legs
    WHERE deleted = FALSE
    GROUP BY shipment_id
) agg
WHERE s.id = agg.shipment_id;

CREATE TABLE IF NOT EXISTS shipment_leg_locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    leg_id UUID NOT NULL REFERENCES shipment_legs(id) ON DELETE CASCADE,
    latitude NUMERIC(12,8) NOT NULL,
    longitude NUMERIC(12,8) NOT NULL,
    speed NUMERIC(10,2),
    heading NUMERIC(10,2),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    provider VARCHAR(100),
    payload_json JSONB
);

CREATE TABLE IF NOT EXISTS shipment_leg_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    leg_id UUID NOT NULL REFERENCES shipment_legs(id) ON DELETE CASCADE,
    document_type VARCHAR(50) NOT NULL,
    file_name VARCHAR(500),
    storage_url VARCHAR(2000),
    content_type VARCHAR(200),
    remarks TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT chk_shipment_leg_document_type CHECK (document_type IN (
        'LR', 'EWAY_BILL', 'INVOICE', 'PACKING_LIST', 'POD', 'PHOTO',
        'TRANSPORT_PERMIT', 'DRIVER_DOCUMENT', 'OTHER'
    ))
);

CREATE INDEX IF NOT EXISTS idx_shipment_legs_org ON shipment_legs(organization_id);
CREATE INDEX IF NOT EXISTS idx_shipment_legs_status ON shipment_legs(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_shipment_legs_origin ON shipment_legs(organization_id, origin_location);
CREATE INDEX IF NOT EXISTS idx_shipment_legs_dest ON shipment_legs(organization_id, destination_location);
CREATE INDEX IF NOT EXISTS idx_shipment_leg_locations_leg ON shipment_leg_locations(leg_id, recorded_at);
CREATE INDEX IF NOT EXISTS idx_shipment_leg_documents_leg ON shipment_leg_documents(leg_id) WHERE deleted = FALSE;
