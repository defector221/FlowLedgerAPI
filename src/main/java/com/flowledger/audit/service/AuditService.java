package com.flowledger.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowledger.audit.dto.AuditLogResponse;
import com.flowledger.audit.entity.AuditLog;
import com.flowledger.audit.repository.AuditLogRepository;
import com.flowledger.auth.entity.User;
import com.flowledger.auth.repository.UserRepository;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    private final UserRepository users;

    public AuditService(AuditLogRepository repository, UserRepository users) {
        this.repository = repository;
        this.users = users;
    }

    public void log(String action, String entityType, UUID entityId, JsonNode oldValue, JsonNode newValue) {
        log(action, entityType, entityId, oldValue, newValue, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(
            String action,
            String entityType,
            UUID entityId,
            JsonNode oldValue,
            JsonNode newValue,
            String ipAddress,
            String userAgent) {
        UUID organizationId = TenantContext.organizationId().orElse(null);
        if (organizationId == null
                || action == null
                || action.isBlank()
                || entityType == null
                || entityType.isBlank()) {
            return;
        }
        AuditLog log = new AuditLog();
        log.setOrganizationId(organizationId);
        log.setUserId(TenantContext.userId().orElse(null));
        log.setAction(truncate(action, 50));
        log.setEntityType(truncate(entityType, 100));
        log.setEntityId(entityId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setIpAddress(truncate(ipAddress, 50));
        log.setUserAgent(truncate(userAgent, 500));
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(
            String search,
            String action,
            String entityType,
            UUID userId,
            LocalDate from,
            LocalDate to,
            Pageable pageable) {
        UUID org = TenantContext.getOrganizationId();
        Specification<AuditLog> spec = (root, query, cb) -> cb.equal(root.get("organizationId"), org);

        if (action != null && !action.isBlank()) {
            String like = "%" + action.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("action")), like));
        }
        if (entityType != null && !entityType.isBlank()) {
            String like = "%" + entityType.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("entityType")), like));
        }
        if (userId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (from != null) {
            var fromTs = from.atStartOfDay().atOffset(ZoneOffset.UTC);
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), fromTs));
        }
        if (to != null) {
            var toTs = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), toTs));
        }
        if (search != null && !search.isBlank()) {
            String raw = search.trim();
            String like = "%" + raw.toLowerCase() + "%";
            UUID entityId = tryParseUuid(raw);
            spec = spec.and((root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.like(cb.lower(root.get("action")), like));
                predicates.add(cb.like(cb.lower(root.get("entityType")), like));
                predicates.add(cb.like(cb.lower(cb.coalesce(root.get("ipAddress"), "")), like));
                if (entityId != null) {
                    predicates.add(cb.equal(root.get("entityId"), entityId));
                }
                return cb.or(predicates.toArray(Predicate[]::new));
            });
        }

        Page<AuditLog> page = repository.findAll(spec, pageable);
        Map<UUID, User> byId = loadUsers(page.getContent());
        return page.map(log -> toResponse(log, byId.get(log.getUserId())));
    }

    @Transactional(readOnly = true)
    public AuditLogResponse get(UUID id) {
        UUID org = TenantContext.getOrganizationId();
        AuditLog log = repository
                .findByIdAndOrganizationId(id, org)
                .orElseThrow(() -> new ResourceNotFoundException("Audit log not found"));
        User user =
                log.getUserId() == null ? null : users.findById(log.getUserId()).orElse(null);
        return toResponse(log, user);
    }

    private Map<UUID, User> loadUsers(List<AuditLog> logs) {
        Set<UUID> ids =
                logs.stream().map(AuditLog::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private static AuditLogResponse toResponse(AuditLog log, User user) {
        String userName = null;
        String userEmail = null;
        if (user != null) {
            userEmail = user.getEmail();
            String first =
                    user.getFirstName() == null ? "" : user.getFirstName().trim();
            String last = user.getLastName() == null ? "" : user.getLastName().trim();
            userName = (first + " " + last).trim();
            if (userName.isBlank()) userName = userEmail;
        }
        return new AuditLogResponse(
                log.getId(),
                log.getOrganizationId(),
                log.getUserId(),
                userName,
                userEmail,
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getOldValue(),
                log.getNewValue(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt());
    }

    private static UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
