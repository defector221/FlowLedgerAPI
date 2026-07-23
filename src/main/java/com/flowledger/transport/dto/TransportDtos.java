package com.flowledger.transport.dto;

import com.flowledger.transport.domain.TransportEnums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TransportDtos {
    private TransportDtos() {}

    public record CompanyRequest(
            @NotBlank String name,
            String code,
            String gstin,
            String pan,
            String email,
            String phone,
            String address,
            String city,
            String state,
            String stateCode,
            String country,
            String status,
            String notes) {}

    public record CompanyResponse(
            UUID id,
            String name,
            String code,
            String gstin,
            String pan,
            String email,
            String phone,
            String address,
            String city,
            String state,
            String stateCode,
            String country,
            String status,
            String notes,
            Long version) {}

    public record VehicleRequest(
            UUID companyId,
            @NotBlank String vehicleNumber,
            @NotBlank String vehicleType,
            BigDecimal capacity,
            String capacityUnit,
            @NotNull VehicleOwnership ownership,
            UUID driverId,
            LocalDate fitnessExpiry,
            LocalDate insuranceExpiry,
            LocalDate permitExpiry,
            VehicleStatus currentStatus,
            String notes) {}

    public record VehicleResponse(
            UUID id,
            UUID companyId,
            String vehicleNumber,
            String vehicleType,
            BigDecimal capacity,
            String capacityUnit,
            VehicleOwnership ownership,
            UUID driverId,
            LocalDate fitnessExpiry,
            LocalDate insuranceExpiry,
            LocalDate permitExpiry,
            VehicleStatus currentStatus,
            String notes,
            Long version) {}

    public record DriverRequest(
            UUID companyId,
            @NotBlank String name,
            @NotBlank String licenseNumber,
            LocalDate licenseExpiry,
            String mobile,
            String emergencyContact,
            UUID assignedVehicleId,
            DriverStatus currentStatus,
            String notes) {}

    public record DriverResponse(
            UUID id,
            UUID companyId,
            String name,
            String licenseNumber,
            LocalDate licenseExpiry,
            String mobile,
            String emergencyContact,
            UUID assignedVehicleId,
            DriverStatus currentStatus,
            String notes,
            Long version) {}

    public record LegRequest(
            Integer sequenceNo,
            UUID transportCompanyId,
            UUID vehicleId,
            UUID driverId,
            String lrNumber,
            String consignmentNumber,
            String vehicleNumberSnapshot,
            String driverNameSnapshot,
            String driverMobileSnapshot,
            OffsetDateTime expectedDeparture,
            OffsetDateTime expectedArrival,
            String remarks,
            TransportMode transportMode,
            String originLocation,
            String destinationLocation,
            String waypointsJson,
            BigDecimal estimatedDistance,
            Integer estimatedDurationMinutes,
            BigDecimal freightCost,
            BigDecimal fuelCost,
            BigDecimal tollCost,
            BigDecimal otherCharges) {
        public LegRequest(
                Integer sequenceNo,
                UUID transportCompanyId,
                UUID vehicleId,
                UUID driverId,
                String lrNumber,
                String consignmentNumber,
                String vehicleNumberSnapshot,
                String driverNameSnapshot,
                String driverMobileSnapshot,
                OffsetDateTime expectedDeparture,
                OffsetDateTime expectedArrival,
                String remarks) {
            this(
                    sequenceNo,
                    transportCompanyId,
                    vehicleId,
                    driverId,
                    lrNumber,
                    consignmentNumber,
                    vehicleNumberSnapshot,
                    driverNameSnapshot,
                    driverMobileSnapshot,
                    expectedDeparture,
                    expectedArrival,
                    remarks,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
    }

    public record LineRequest(
            UUID sourceLineId,
            UUID productId,
            String description,
            @NotNull @Positive BigDecimal quantity,
            UUID unitId,
            String batchNumber,
            String serialNumber,
            Integer lineOrder) {}

    public record ShipmentRequest(
            String sourceDocumentType,
            UUID sourceDocumentId,
            boolean transportRequired,
            TransportMode transportMode,
            TransportType transportType,
            UUID transportCompanyId,
            UUID fromWarehouseId,
            String shipToPartyType,
            UUID shipToPartyId,
            String shipToAddress,
            OffsetDateTime expectedDispatchDate,
            OffsetDateTime expectedDeliveryDate,
            BigDecimal freightCharges,
            FreightPayer freightPaidBy,
            String insuranceDetails,
            String gpsTrackingUrl,
            String ewayBillNumber,
            String einvoiceReference,
            String remarks,
            @Valid List<LegRequest> legs,
            @Valid @NotEmpty List<LineRequest> lines) {}

    public record AssignmentRequest(@Valid @NotEmpty List<LegRequest> legs, String remarks) {}

    public record TransitionRequest(String remarks, String locationJson, String payloadJson) {}

    public record ManualEventRequest(String eventType, String remarks, String locationJson, String payloadJson) {}

    public record DecisionRequest(String remarks) {}

    public record LegStatusRequest(@NotNull ShipmentLegStatus status, String remarks) {}

    public record LegLocationRequest(
            @NotNull BigDecimal latitude,
            @NotNull BigDecimal longitude,
            BigDecimal speed,
            BigDecimal heading,
            String provider,
            String payloadJson,
            String remarks) {}

    public record LegDocumentRequest(
            @NotNull ShipmentLegDocumentType documentType,
            String fileName,
            String storageUrl,
            String contentType,
            String remarks) {}

    public record ChallanShipmentRequest(
            TransportMode transportMode,
            TransportType transportType,
            BigDecimal freightCharges,
            FreightPayer freightPaidBy,
            UUID transportCompanyId,
            String shipToAddress,
            OffsetDateTime expectedDispatchDate,
            OffsetDateTime expectedDeliveryDate,
            String ewayBillNumber,
            String remarks,
            @Valid List<LineRequest> lines) {}

    public record ShipmentHeaderRequest(
            TransportMode transportMode,
            TransportType transportType,
            UUID transportCompanyId,
            String shipToAddress,
            OffsetDateTime expectedDispatchDate,
            OffsetDateTime expectedDeliveryDate,
            BigDecimal freightCharges,
            FreightPayer freightPaidBy,
            String ewayBillNumber,
            String remarks) {}

    public record LegResponse(
            UUID id,
            int sequenceNo,
            UUID transportCompanyId,
            UUID vehicleId,
            UUID driverId,
            String lrNumber,
            String consignmentNumber,
            String vehicleNumberSnapshot,
            String driverNameSnapshot,
            String driverMobileSnapshot,
            OffsetDateTime expectedDeparture,
            OffsetDateTime expectedArrival,
            OffsetDateTime actualDeparture,
            OffsetDateTime actualArrival,
            String remarks,
            ShipmentLegStatus status,
            TransportMode transportMode,
            String originLocation,
            String destinationLocation,
            String waypointsJson,
            BigDecimal estimatedDistance,
            BigDecimal actualDistance,
            Integer estimatedDurationMinutes,
            Integer actualDurationMinutes,
            BigDecimal freightCost,
            BigDecimal fuelCost,
            BigDecimal tollCost,
            BigDecimal otherCharges,
            BigDecimal currentLatitude,
            BigDecimal currentLongitude,
            OffsetDateTime locationUpdatedAt,
            BigDecimal currentSpeed,
            BigDecimal vehicleHeading,
            String gpsProvider) {}

    public record LineResponse(
            UUID id,
            UUID sourceLineId,
            UUID productId,
            String description,
            BigDecimal quantity,
            UUID unitId,
            String batchNumber,
            String serialNumber,
            int lineOrder) {}

    public record ShipmentCostDTO(
            BigDecimal freightCharges,
            BigDecimal fuelChargesTotal,
            BigDecimal tollChargesTotal,
            BigDecimal otherChargesTotal,
            BigDecimal grandTotal) {}

    public record ShipmentResponse(
            UUID id,
            String shipmentNumber,
            ShipmentStatus status,
            String sourceDocumentType,
            UUID sourceDocumentId,
            boolean transportRequired,
            TransportMode transportMode,
            TransportType transportType,
            UUID transportCompanyId,
            UUID fromWarehouseId,
            String shipToPartyType,
            UUID shipToPartyId,
            String shipToAddress,
            OffsetDateTime expectedDispatchDate,
            OffsetDateTime expectedDeliveryDate,
            OffsetDateTime actualDispatchDate,
            OffsetDateTime actualDeliveryDate,
            BigDecimal freightCharges,
            FreightPayer freightPaidBy,
            String insuranceDetails,
            String gpsTrackingUrl,
            String ewayBillNumber,
            String einvoiceReference,
            String remarks,
            List<LegResponse> legs,
            List<LineResponse> lines,
            Long version,
            String priority,
            BigDecimal totalDistance,
            BigDecimal fuelChargesTotal,
            BigDecimal tollChargesTotal,
            BigDecimal otherChargesTotal,
            BigDecimal grandTotal,
            String transportCompanyName,
            String shipToPartyName,
            String fromWarehouseName) {}

    /** Alias detail DTO — same payload as ShipmentResponse for OpenAPI clarity. */
    public record ShipmentDetailDTO(
            UUID id,
            String shipmentNumber,
            ShipmentStatus status,
            String sourceDocumentType,
            UUID sourceDocumentId,
            boolean transportRequired,
            TransportMode transportMode,
            TransportType transportType,
            List<LegResponse> legs,
            List<LineResponse> lines,
            ShipmentCostDTO costs,
            Long version) {}

    public record ShipmentSummaryDTO(
            UUID id, String shipmentNumber, ShipmentStatus status, BigDecimal grandTotal, int legCount) {}

    public record ShipmentTimelineDTO(
            UUID id,
            String eventType,
            OffsetDateTime occurredAt,
            UUID actorUserId,
            ShipmentActorType actorType,
            String remarks,
            String locationJson,
            String payloadJson) {}

    public record LegDocumentResponse(
            UUID id,
            UUID legId,
            ShipmentLegDocumentType documentType,
            String fileName,
            String storageUrl,
            String contentType,
            String remarks,
            OffsetDateTime createdAt) {}

    public record LegLocationResponse(
            UUID id,
            UUID legId,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal speed,
            BigDecimal heading,
            OffsetDateTime recordedAt,
            String provider) {}

    public record SearchRequest(
            String q,
            String vehicleNumber,
            String driverName,
            String driverMobile,
            String company,
            String lrNumber,
            String ewayBillNumber,
            ShipmentStatus status,
            LocalDate fromDate,
            LocalDate toDate,
            UUID customerId,
            UUID warehouseId,
            String sourceDocumentType,
            UUID sourceDocumentId,
            String sku,
            String origin,
            String destination,
            TransportMode transportMode) {
        public SearchRequest(
                String q,
                String vehicleNumber,
                String driverName,
                String driverMobile,
                String company,
                String lrNumber,
                String ewayBillNumber,
                ShipmentStatus status,
                LocalDate fromDate,
                LocalDate toDate,
                UUID customerId,
                UUID warehouseId,
                String sourceDocumentType,
                UUID sourceDocumentId,
                String sku) {
            this(
                    q,
                    vehicleNumber,
                    driverName,
                    driverMobile,
                    company,
                    lrNumber,
                    ewayBillNumber,
                    status,
                    fromDate,
                    toDate,
                    customerId,
                    warehouseId,
                    sourceDocumentType,
                    sourceDocumentId,
                    sku,
                    null,
                    null,
                    null);
        }
    }

    public record TimelineEvent(
            UUID id,
            String eventType,
            OffsetDateTime occurredAt,
            UUID actorUserId,
            ShipmentActorType actorType,
            String remarks,
            String locationJson,
            String payloadJson) {}

    public record TransportRegisterRow(
            UUID shipmentId,
            String shipmentNumber,
            ShipmentStatus status,
            String vehicleNumber,
            String driverName,
            String company,
            OffsetDateTime dispatchDate,
            OffsetDateTime deliveryDate,
            BigDecimal freightCharges) {}
}
