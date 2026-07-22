-- Shipment workflow, approvals, events, and integration outbox

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE approval_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    requested_by UUID NOT NULL REFERENCES users(id),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_by UUID REFERENCES users(id),
    decided_at TIMESTAMPTZ,
    remarks TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT chk_approval_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

CREATE TABLE approval_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES approval_requests(id) ON DELETE CASCADE,
    action VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL REFERENCES users(id),
    acted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    remarks TEXT
);

CREATE TABLE shipments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    shipment_number VARCHAR(100) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    source_document_type VARCHAR(50),
    source_document_id UUID,
    transport_required BOOLEAN NOT NULL DEFAULT FALSE,
    transport_mode VARCHAR(30),
    transport_type VARCHAR(30),
    transport_company_id UUID REFERENCES transport_companies(id),
    from_warehouse_id UUID REFERENCES warehouses(id),
    ship_to_party_type VARCHAR(50),
    ship_to_party_id UUID,
    ship_to_address TEXT,
    expected_dispatch_date TIMESTAMPTZ,
    expected_delivery_date TIMESTAMPTZ,
    actual_dispatch_date TIMESTAMPTZ,
    actual_delivery_date TIMESTAMPTZ,
    freight_charges NUMERIC(19,4) NOT NULL DEFAULT 0,
    freight_paid_by VARCHAR(30),
    insurance_details TEXT,
    gps_tracking_url VARCHAR(1000),
    eway_bill_number VARCHAR(100),
    einvoice_reference VARCHAR(255),
    remarks TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_shipments_org_number UNIQUE (organization_id, shipment_number),
    CONSTRAINT chk_shipment_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'APPROVED', 'ASSIGNED', 'LOADING', 'LOADED',
        'PARTIALLY_DISPATCHED', 'DISPATCHED', 'IN_TRANSIT', 'DELIVERED',
        'CLOSED', 'CANCELLED', 'REJECTED'
    )),
    CONSTRAINT chk_shipment_mode CHECK (transport_mode IS NULL OR transport_mode IN (
        'ROAD', 'RAIL', 'AIR', 'SEA', 'COURIER', 'CUSTOMER_PICKUP', 'INTERNAL_VEHICLE'
    )),
    CONSTRAINT chk_shipment_type CHECK (transport_type IS NULL OR transport_type IN (
        'SELF', 'THIRD_PARTY', 'CUSTOMER_ARRANGED'
    )),
    CONSTRAINT chk_shipment_freight_payer CHECK (freight_paid_by IS NULL OR freight_paid_by IN (
        'SENDER', 'RECEIVER', 'THIRD_PARTY'
    ))
);

CREATE TABLE shipment_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    source_line_id UUID,
    product_id UUID REFERENCES products(id),
    description VARCHAR(500),
    quantity NUMERIC(19,4) NOT NULL,
    unit_id UUID REFERENCES units(id),
    batch_number VARCHAR(100),
    serial_number VARCHAR(255),
    line_order INT NOT NULL DEFAULT 0
);

CREATE TABLE shipment_legs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    sequence_no INT NOT NULL,
    transport_company_id UUID REFERENCES transport_companies(id),
    vehicle_id UUID REFERENCES transport_vehicles(id),
    driver_id UUID REFERENCES transport_drivers(id),
    lr_number VARCHAR(100),
    consignment_number VARCHAR(100),
    vehicle_number_snapshot VARCHAR(50),
    driver_name_snapshot VARCHAR(200),
    driver_mobile_snapshot VARCHAR(30),
    expected_departure TIMESTAMPTZ,
    expected_arrival TIMESTAMPTZ,
    actual_departure TIMESTAMPTZ,
    actual_arrival TIMESTAMPTZ,
    remarks TEXT,
    CONSTRAINT uq_shipment_legs_sequence UNIQUE (shipment_id, sequence_no)
);

CREATE TABLE shipment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_user_id UUID REFERENCES users(id),
    actor_type VARCHAR(20) NOT NULL,
    remarks TEXT,
    location_json JSONB,
    payload_json JSONB,
    CONSTRAINT chk_shipment_event_actor_type CHECK (actor_type IN ('USER', 'SYSTEM'))
);

CREATE TABLE shipment_external_refs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID NOT NULL REFERENCES shipments(id) ON DELETE CASCADE,
    provider_type VARCHAR(100) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    status VARCHAR(50),
    payload_json JSONB,
    last_synced_at TIMESTAMPTZ,
    CONSTRAINT uq_shipment_external_ref UNIQUE (shipment_id, provider_type, external_id)
);

CREATE TABLE integration_outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload_json JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_integration_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD'))
);

ALTER TABLE delivery_challans
    ADD COLUMN IF NOT EXISTS transport_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE delivery_challan_items
    ADD COLUMN IF NOT EXISTS quantity_dispatched NUMERIC(19,4) NOT NULL DEFAULT 0;

CREATE INDEX idx_approval_requests_org_entity ON approval_requests(organization_id, entity_type, entity_id);
CREATE INDEX idx_approval_requests_org_status ON approval_requests(organization_id, status);
CREATE INDEX idx_approval_actions_request ON approval_actions(request_id);
CREATE INDEX idx_shipments_source ON shipments(organization_id, source_document_type, source_document_id);
CREATE INDEX idx_shipments_status ON shipments(organization_id, status);
CREATE INDEX idx_shipments_dates ON shipments(organization_id, expected_dispatch_date, expected_delivery_date);
CREATE INDEX idx_shipments_eway ON shipments(organization_id, eway_bill_number);
CREATE INDEX idx_shipment_lines_shipment ON shipment_lines(shipment_id);
CREATE INDEX idx_shipment_legs_shipment ON shipment_legs(shipment_id);
CREATE INDEX idx_shipment_legs_vehicle ON shipment_legs(vehicle_id);
CREATE INDEX idx_shipment_legs_lr ON shipment_legs(lr_number);
CREATE INDEX idx_shipment_events_timeline ON shipment_events(shipment_id, occurred_at);
CREATE INDEX idx_shipment_external_refs_shipment ON shipment_external_refs(shipment_id);
CREATE INDEX idx_integration_outbox_dispatch ON integration_outbox(status, next_attempt_at, created_at);
CREATE INDEX idx_integration_outbox_aggregate ON integration_outbox(organization_id, aggregate_type, aggregate_id);

CREATE INDEX idx_transport_vehicles_number_trgm
    ON transport_vehicles USING GIN (vehicle_number gin_trgm_ops);
CREATE INDEX idx_transport_drivers_name_trgm
    ON transport_drivers USING GIN (name gin_trgm_ops);
CREATE INDEX idx_transport_companies_name_trgm
    ON transport_companies USING GIN (name gin_trgm_ops);
CREATE INDEX idx_shipment_legs_lr_trgm
    ON shipment_legs USING GIN (lr_number gin_trgm_ops);
CREATE INDEX idx_shipments_eway_trgm
    ON shipments USING GIN (eway_bill_number gin_trgm_ops);
CREATE INDEX idx_shipments_number_trgm
    ON shipments USING GIN (shipment_number gin_trgm_ops);
