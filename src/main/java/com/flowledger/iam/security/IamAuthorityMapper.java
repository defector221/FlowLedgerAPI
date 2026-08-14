package com.flowledger.iam.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Bridges IAM product-scoped permissions/roles onto Spring authorities, including temporary legacy aliases
 * so existing {@code @PreAuthorize} annotations keep working during progressive migration.
 */
@Component
public class IamAuthorityMapper {

    private static final Map<String, List<String>> PRODUCT_TO_LEGACY = Map.ofEntries(
            Map.entry("flowledger.ai.chat", List.of("AI_CHAT")),
            Map.entry("flowledger.invoice.read", List.of("SALES_READ")),
            Map.entry("flowledger.invoice.create", List.of("SALES_WRITE", "SALES_READ")),
            Map.entry("flowledger.invoice.update", List.of("SALES_WRITE", "SALES_READ")),
            Map.entry("flowledger.invoice.approve", List.of("SALES_WRITE", "SALES_READ")),
            Map.entry("flowledger.invoice.delete", List.of("SALES_WRITE")),
            Map.entry("AI_CHAT", List.of("flowledger.ai.chat")),
            Map.entry("SALES_READ", List.of("flowledger.invoice.read")),
            Map.entry("SALES_WRITE", List.of("flowledger.invoice.create", "flowledger.invoice.update")));

    public Collection<String> toAuthorities(Collection<String> roles, Collection<String> permissions) {
        Set<String> out = new LinkedHashSet<>();
        if (permissions != null) {
            for (String permission : permissions) {
                if (permission == null || permission.isBlank()) continue;
                String code = permission.trim();
                out.add(code);
                List<String> aliases = PRODUCT_TO_LEGACY.get(code);
                if (aliases != null) out.addAll(aliases);
                // Wildcard expansion for enforcement convenience on exact checks already in claims
                if (code.endsWith(".*")) {
                    out.add(code);
                }
            }
        }
        if (roles != null) {
            for (String role : roles) {
                if (role == null || role.isBlank()) continue;
                String code = role.trim();
                out.add(code.startsWith("ROLE_") ? code : "ROLE_" + code.toUpperCase(Locale.ROOT));
                // Keep raw role code too for hasAuthority(role) styles
                out.add(code);
            }
        }
        return List.copyOf(out);
    }

    public List<String> extractPermissions(Map<String, Object> claims) {
        List<String> permissions = new ArrayList<>();
        addStrings(permissions, claims.get("permissions"));
        addStrings(permissions, claims.get("permission"));
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            addStrings(permissions, map.get("roles"));
        }
        return permissions;
    }

    public List<String> extractRoles(Map<String, Object> claims) {
        List<String> roles = new ArrayList<>();
        addStrings(roles, claims.get("productRoles"));
        addStrings(roles, claims.get("roles"));
        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            addStrings(roles, map.get("roles"));
        }
        return roles;
    }

    @SuppressWarnings("unchecked")
    private static void addStrings(List<String> target, Object value) {
        if (value == null) return;
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null) target.add(item.toString());
            }
            return;
        }
        if (value instanceof String s) {
            if (s.startsWith("[") && s.endsWith("]")) {
                // JSON-ish array serialized as string
                String inner = s.substring(1, s.length() - 1).trim();
                if (!inner.isEmpty()) {
                    for (String part : inner.split(",")) {
                        String cleaned = part.trim().replace("\"", "");
                        if (!cleaned.isEmpty()) target.add(cleaned);
                    }
                }
            } else if (!s.isBlank()) {
                target.add(s);
            }
        }
    }
}
