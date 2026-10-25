package com.assesshub.common;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class TextUtils {

    private TextUtils() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    /** "1,5,9" -> {1,5,9}; tolerant of blanks and whitespace. */
    public static Set<Long> parseIdList(String csv) {
        if (isBlank(csv)) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static String joinIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().distinct().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Newline separated list stored in TEXT columns (accepted short answers). */
    public static List<String> splitLines(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static String joinLines(Collection<String> values) {
        if (values == null) {
            return null;
        }
        String joined = values.stream().map(v -> v == null ? "" : v.trim()).filter(v -> !v.isEmpty())
                .collect(Collectors.joining("\n"));
        return joined.isEmpty() ? null : joined;
    }

    public static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public static String likePattern(String search) {
        String s = trimToNull(search);
        return s == null ? null : "%" + s.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
