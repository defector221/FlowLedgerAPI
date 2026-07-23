package com.flowledger.transport.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.transport.domain.TransportEnums.*;
import com.flowledger.transport.entity.*;
import com.flowledger.transport.repository.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ApprovalService {
    private final ApprovalRequestRepository requests;
    private final ApprovalActionRepository actions;
    private final OrganizationSettingsRepository settings;
    private final ShipmentEventRepository events;
    private final TransportActivityNotificationService activityNotifications;

    public ApprovalService(
            ApprovalRequestRepository requests,
            ApprovalActionRepository actions,
            OrganizationSettingsRepository settings,
            ShipmentEventRepository events,
            TransportActivityNotificationService activityNotifications) {
        this.requests = requests;
        this.actions = actions;
        this.settings = settings;
        this.events = events;
        this.activityNotifications = activityNotifications;
    }

    public boolean approvalRequired() {
        return settings.findByOrganizationId(TenantContext.getOrganizationId())
                .map(s -> s.isTransportApprovalRequired())
                .orElse(false);
    }

    public ApprovalRequest submit(Shipment shipment, String remarks) {
        UUID user = user();
        ApprovalRequest request = new ApprovalRequest();
        request.setOrganizationId(TenantContext.getOrganizationId());
        request.setEntityType("SHIPMENT");
        request.setEntityId(shipment.getId());
        request.setRequestedBy(user);
        request.setRequestedAt(OffsetDateTime.now());
        request.setRemarks(remarks);
        request.setCreatedBy(user);
        request.setUpdatedBy(user);
        request = requests.save(request);
        action(request.getId(), "SUBMITTED", remarks);
        event(shipment, "APPROVAL_REQUESTED", remarks);
        return request;
    }

    public void approve(Shipment shipment, String remarks) {
        decide(shipment, ApprovalStatus.APPROVED, remarks);
    }

    public void reject(Shipment shipment, String remarks) {
        decide(shipment, ApprovalStatus.REJECTED, remarks);
    }

    private void decide(Shipment shipment, ApprovalStatus decision, String remarks) {
        ApprovalRequest request =
                requests.findFirstByOrganizationIdAndEntityTypeAndEntityIdAndStatusOrderByRequestedAtDesc(
                                TenantContext.getOrganizationId(), "SHIPMENT", shipment.getId(), ApprovalStatus.PENDING)
                        .orElseThrow(
                                () -> new ResponseStatusException(HttpStatus.CONFLICT, "No pending approval request"));
        UUID user = user();
        request.setStatus(decision);
        request.setDecidedBy(user);
        request.setDecidedAt(OffsetDateTime.now());
        request.setRemarks(remarks);
        request.setUpdatedBy(user);
        action(request.getId(), decision.name(), remarks);
        event(shipment, "APPROVAL_" + decision.name(), remarks);
    }

    private void action(UUID requestId, String value, String remarks) {
        ApprovalAction action = new ApprovalAction();
        action.setRequestId(requestId);
        action.setAction(value);
        action.setActorId(user());
        action.setActedAt(OffsetDateTime.now());
        action.setRemarks(remarks);
        actions.save(action);
    }

    private void event(Shipment shipment, String type, String remarks) {
        ShipmentEvent event = new ShipmentEvent();
        event.setShipmentId(shipment.getId());
        event.setEventType(type);
        event.setOccurredAt(OffsetDateTime.now());
        event.setActorUserId(user());
        event.setActorType(ShipmentActorType.USER);
        event.setRemarks(remarks);
        events.save(event);
        activityNotifications.notifyShipmentEvent(shipment, type, remarks);
    }

    private UUID user() {
        return TenantContext.userId()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user required"));
    }
}
