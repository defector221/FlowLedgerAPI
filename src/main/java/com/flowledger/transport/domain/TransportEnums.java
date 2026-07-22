package com.flowledger.transport.domain;

public final class TransportEnums {
    private TransportEnums() {}

    public enum ApprovalStatus {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    public enum ShipmentStatus {
        DRAFT,
        SUBMITTED,
        APPROVED,
        ASSIGNED,
        LOADING,
        LOADED,
        PARTIALLY_DISPATCHED,
        DISPATCHED,
        IN_TRANSIT,
        DELIVERED,
        CLOSED,
        CANCELLED,
        REJECTED
    }

    public enum TransportMode {
        ROAD,
        RAIL,
        AIR,
        SEA,
        COURIER,
        CUSTOMER_PICKUP,
        INTERNAL_VEHICLE
    }

    public enum TransportType {
        SELF,
        THIRD_PARTY,
        CUSTOMER_ARRANGED
    }

    public enum FreightPayer {
        SENDER,
        RECEIVER,
        THIRD_PARTY
    }

    public enum VehicleOwnership {
        SELF,
        THIRD_PARTY
    }

    public enum VehicleStatus {
        AVAILABLE,
        IN_TRANSIT,
        MAINTENANCE,
        INACTIVE
    }

    public enum DriverStatus {
        AVAILABLE,
        ON_TRIP,
        INACTIVE
    }

    public enum ShipmentActorType {
        USER,
        SYSTEM
    }

    public enum OutboxStatus {
        PENDING,
        SENT,
        FAILED,
        DEAD
    }
}
