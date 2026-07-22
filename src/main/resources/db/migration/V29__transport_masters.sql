-- Organization-scoped transport master data

CREATE TABLE transport_companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(50) NOT NULL,
    gstin VARCHAR(20),
    pan VARCHAR(20),
    email VARCHAR(255),
    phone VARCHAR(30),
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    state_code VARCHAR(10),
    country VARCHAR(100) DEFAULT 'India',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_transport_companies_org_code UNIQUE (organization_id, code)
);

CREATE TABLE transport_company_branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES transport_companies(id),
    name VARCHAR(200) NOT NULL,
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    phone VARCHAR(30),
    contact_name VARCHAR(200),
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE transport_company_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES transport_companies(id),
    name VARCHAR(200) NOT NULL,
    role VARCHAR(100),
    mobile VARCHAR(30),
    email VARCHAR(255),
    primary_contact BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE transport_service_areas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES transport_companies(id),
    state_code VARCHAR(10),
    state_name VARCHAR(100),
    city VARCHAR(100),
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE transport_preferred_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES transport_companies(id),
    from_location VARCHAR(255) NOT NULL,
    to_location VARCHAR(255) NOT NULL,
    via VARCHAR(500),
    distance_km NUMERIC(12,2),
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID
);

CREATE TABLE transport_rate_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    company_id UUID NOT NULL REFERENCES transport_companies(id),
    route_id UUID REFERENCES transport_preferred_routes(id),
    vehicle_type VARCHAR(100) NOT NULL,
    rate_amount NUMERIC(19,4) NOT NULL,
    rate_unit VARCHAR(50) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    valid_from DATE,
    valid_to DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT chk_transport_rate_card_dates CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE TABLE transport_drivers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    company_id UUID REFERENCES transport_companies(id),
    name VARCHAR(200) NOT NULL,
    license_number VARCHAR(100) NOT NULL,
    license_expiry DATE,
    mobile VARCHAR(30),
    emergency_contact VARCHAR(30),
    assigned_vehicle_id UUID,
    current_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT chk_transport_driver_status CHECK (current_status IN ('AVAILABLE', 'ON_TRIP', 'INACTIVE'))
);

CREATE TABLE transport_vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    company_id UUID REFERENCES transport_companies(id),
    vehicle_number VARCHAR(50) NOT NULL,
    vehicle_type VARCHAR(100) NOT NULL,
    capacity NUMERIC(19,4),
    capacity_unit VARCHAR(30),
    ownership VARCHAR(30) NOT NULL,
    driver_id UUID REFERENCES transport_drivers(id),
    fitness_expiry DATE,
    insurance_expiry DATE,
    permit_expiry DATE,
    current_status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE',
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    CONSTRAINT uq_transport_vehicles_org_number UNIQUE (organization_id, vehicle_number),
    CONSTRAINT chk_transport_vehicle_ownership CHECK (ownership IN ('SELF', 'THIRD_PARTY')),
    CONSTRAINT chk_transport_vehicle_status CHECK (current_status IN ('AVAILABLE', 'IN_TRANSIT', 'MAINTENANCE', 'INACTIVE'))
);

ALTER TABLE transport_drivers
    ADD CONSTRAINT fk_transport_driver_assigned_vehicle
    FOREIGN KEY (assigned_vehicle_id) REFERENCES transport_vehicles(id);

CREATE INDEX idx_transport_companies_org ON transport_companies(organization_id);
CREATE INDEX idx_transport_branches_org_company ON transport_company_branches(organization_id, company_id);
CREATE INDEX idx_transport_contacts_org_company ON transport_company_contacts(organization_id, company_id);
CREATE INDEX idx_transport_service_areas_org_company ON transport_service_areas(organization_id, company_id);
CREATE INDEX idx_transport_routes_org_company ON transport_preferred_routes(organization_id, company_id);
CREATE INDEX idx_transport_rate_cards_org_company ON transport_rate_cards(organization_id, company_id);
CREATE INDEX idx_transport_vehicles_org ON transport_vehicles(organization_id);
CREATE INDEX idx_transport_vehicles_number ON transport_vehicles(vehicle_number);
CREATE INDEX idx_transport_drivers_org ON transport_drivers(organization_id);
CREATE INDEX idx_transport_drivers_license ON transport_drivers(license_number);
