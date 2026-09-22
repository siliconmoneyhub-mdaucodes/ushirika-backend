package com.mdau.ushirika.common.util;

import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Parses a client-supplied "field,direction" sort string (e.g. "createdAt,desc") against a
 * whitelist of real, sortable entity field names, falling back to a caller-supplied default
 * when the param is absent, malformed, or names a field outside the whitelist -- an admin list
 * endpoint should never let an arbitrary string become a raw JPA property path.
 */
public final class SortParams {

    private SortParams() {}

    public static Sort parse(String raw, Set<String> allowedFields, Sort defaultSort) {
        if (raw == null || raw.isBlank()) return defaultSort;
        String[] parts = raw.split(",", 2);
        String field = parts[0].trim();
        if (field.isEmpty() || !allowedFields.contains(field)) return defaultSort;
        boolean desc = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc");
        return Sort.by(desc ? Sort.Direction.DESC : Sort.Direction.ASC, field);
    }
}
