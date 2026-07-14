package com.flowledger.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowledger.audit.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records an audit log entry for successful mutating REST controller calls
 * (POST / PUT / PATCH / DELETE) within the tenant context.
 */
@Aspect
@Component
@Slf4j
public class AuditActionAspect {
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Set<String> SKIP_CONTROLLERS = Set.of(
            "AuditController",
            "AuthController",
            "GstCalculationController",
            "SearchController",
            "DashboardController",
            "ReportController");
    private static final Set<String> SKIP_METHODS = Set.of("preview", "calculate", "previewAudience", "reindex");

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AuditActionAspect(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController) && ("
            + "@annotation(org.springframework.web.bind.annotation.PostMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PatchMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.DeleteMapping))")
    public Object auditMutatingRequest(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        try {
            recordAudit(pjp, result);
        } catch (Exception ex) {
            log.warn("Failed to write audit log for {}: {}", pjp.getSignature().toShortString(), ex.getMessage());
        }
        return result;
    }

    private void recordAudit(ProceedingJoinPoint pjp, Object result) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        String controller = method.getDeclaringClass().getSimpleName();
        if (SKIP_CONTROLLERS.contains(controller)) return;
        if (SKIP_METHODS.contains(method.getName())) return;
        if (method.getName().toLowerCase().contains("preview")
                || method.getName().toLowerCase().contains("calculate")) {
            return;
        }

        String entityType = toEntityType(controller);
        String action = toAction(method);
        UUID entityId = extractEntityId(pjp.getArgs()).or(() -> extractIdFromResult(result)).orElse(null);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("controller", controller);
        detail.put("method", method.getName());
        detail.put("httpMethod", httpMethod(method));
        if (entityId != null) detail.put("entityId", entityId.toString());

        HttpServletRequest request = currentRequest();
        String ip = request == null ? null : clientIp(request);
        String ua = request == null ? null : request.getHeader("User-Agent");
        if (request != null) detail.put("path", request.getRequestURI());
        JsonNode requestBody = extractRequestBody(pjp.getArgs());
        if (requestBody != null && !requestBody.isNull()) detail.set("request", requestBody);
        JsonNode resultSummary = summarizeResult(result);
        if (resultSummary != null) detail.set("result", resultSummary);

        auditService.log(action, entityType, entityId, null, detail, ip, ua);
    }

    private JsonNode extractRequestBody(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg == null || arg instanceof UUID || arg instanceof HttpServletRequest) continue;
            if (arg instanceof String || arg instanceof Number || arg instanceof Boolean) continue;
            try {
                JsonNode node = objectMapper.valueToTree(arg);
                if (node != null && node.isObject()) return redactSensitive(node.deepCopy());
                if (node != null && node.isArray() && !node.isEmpty()) return node;
            } catch (Exception ignored) {
                // skip non-serializable args (MultipartFile, etc.)
            }
        }
        return null;
    }

    private static JsonNode redactSensitive(JsonNode node) {
        if (!(node instanceof ObjectNode object)) return node;
        for (String key : List.of("password", "passwordHash", "accessToken", "refreshToken", "token", "secret")) {
            if (object.has(key)) object.put(key, "[redacted]");
        }
        return object;
    }

    private JsonNode summarizeResult(Object result) {
        if (result == null) return null;
        try {
            JsonNode node = objectMapper.valueToTree(result);
            if (node == null || node.isNull()) return null;
            if (node.isObject()) {
                ObjectNode summary = objectMapper.createObjectNode();
                for (String key : List.of("id", "status", "invoiceNumber", "documentNumber", "name", "email", "templateName")) {
                    if (node.has(key)) summary.set(key, node.get(key));
                }
                return summary.isEmpty() ? null : summary;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private static String toEntityType(String controllerName) {
        if (controllerName.endsWith("Controller")) {
            return controllerName.substring(0, controllerName.length() - "Controller".length());
        }
        return controllerName;
    }

    private static String toAction(Method method) {
        String name = method.getName();
        String upper = name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
        if (upper.length() <= 50) return upper;
        return upper.substring(0, 50);
    }

    private static String httpMethod(Method method) {
        if (method.getAnnotation(PostMapping.class) != null) return "POST";
        if (method.getAnnotation(PutMapping.class) != null) return "PUT";
        if (method.getAnnotation(PatchMapping.class) != null) return "PATCH";
        if (method.getAnnotation(DeleteMapping.class) != null) return "DELETE";
        return "MUTATE";
    }

    private Optional<UUID> extractEntityId(Object[] args) {
        if (args == null) return Optional.empty();
        for (Object arg : args) {
            if (arg instanceof UUID uuid) return Optional.of(uuid);
            if (arg instanceof String s) {
                Matcher matcher = UUID_PATTERN.matcher(s);
                if (matcher.matches()) {
                    try {
                        return Optional.of(UUID.fromString(s));
                    } catch (IllegalArgumentException ignored) {
                        // continue
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<UUID> extractIdFromResult(Object result) {
        if (result == null) return Optional.empty();
        if (result instanceof UUID uuid) return Optional.of(uuid);
        try {
            JsonNode node = objectMapper.valueToTree(result);
            if (node != null && node.hasNonNull("id")) {
                return Optional.of(UUID.fromString(node.get("id").asText()));
            }
        } catch (Exception ignored) {
            // ignore serialization failures for opaque return types
        }
        try {
            Method getId = result.getClass().getMethod("getId");
            Object id = getId.invoke(result);
            if (id instanceof UUID uuid) return Optional.of(uuid);
            if (id != null) return Optional.of(UUID.fromString(id.toString()));
        } catch (Exception ignored) {
            // no id accessor
        }
        return Optional.empty();
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
