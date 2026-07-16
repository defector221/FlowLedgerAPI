package com.flowledger.common.util;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Builds org-scoped business codes from a human name plus a short uniqueness salt.
 *
 * <p>Example: {@code "YRV Solutions"} → {@code YRV-SOLUT-A7K2}
 */
public final class EntityCodeGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] SALT_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int MAX_ATTEMPTS = 25;

    private EntityCodeGenerator() {}

    public static String fromName(String name) {
        return fromName(name, null);
    }

    public static String fromName(String name, String prefix) {
        String slug = slugify(name);
        if (slug.isBlank()) {
            slug = "ITEM";
        }
        if (slug.length() > 12) {
            slug = slug.substring(0, 12);
        }
        String base = (prefix == null || prefix.isBlank()) ? slug : prefix.toUpperCase(Locale.ROOT) + "-" + slug;
        return base + "-" + salt(4);
    }

    /**
     * Generates a unique code using {@code exists} as the collision check. Retries with a new salt on
     * conflict.
     */
    public static String uniqueFromName(String name, String prefix, Predicate<String> exists) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = fromName(name, prefix);
            if (!exists.test(candidate)) {
                return candidate;
            }
        }
        // Extremely unlikely; append epoch fragment for uniqueness
        String fallback = fromName(name, prefix)
                + Long.toString(System.currentTimeMillis() % 100000, 36).toUpperCase(Locale.ROOT);
        if (exists.test(fallback)) {
            throw new IllegalStateException("Unable to generate a unique code for: " + name);
        }
        return fallback;
    }

    public static String slugify(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        // Prefer compact readable segments: keep first 2 words-ish joined by hyphen when possible
        String[] parts = normalized.split("-");
        if (parts.length == 1) {
            return parts[0];
        }
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('-');
            }
            out.append(part.length() > 6 ? part.substring(0, 6) : part);
            used++;
            if (used >= 2 || out.length() >= 12) {
                break;
            }
        }
        String result = out.toString();
        return result.length() > 12 ? result.substring(0, 12) : result;
    }

    public static String salt(int length) {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = SALT_ALPHABET[RANDOM.nextInt(SALT_ALPHABET.length)];
        }
        return new String(chars);
    }
}
