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
            String remarks) {}

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

    public record DecisionRequest(String remarks) {}

    public record ChallanShipmentRequest(@Valid @NotEmpty List<LineRequest> lines) {}

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
            String remarks) {}

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
            Long version) {}

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
            String sku) {}

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
