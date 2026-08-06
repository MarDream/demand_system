package com.demand.system.module.workflow.support;

import com.demand.system.module.workflow.entity.WorkflowVersion;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

public final class WorkflowVersionUtils {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^[1-9]\\d*(?:\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*))?$");

    private WorkflowVersionUtils() {
    }

    public static String normalize(String version) {
        if (!StringUtils.hasText(version)) {
            return null;
        }
        String normalized = version.trim();
        if (normalized.length() > 1
                && (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V')
                && Character.isDigit(normalized.charAt(1))) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    public static boolean isValid(String version) {
        String normalized = normalize(version);
        return normalized != null && VERSION_PATTERN.matcher(normalized).matches();
    }

    public static boolean sameVersion(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft == null || normalizedRight == null) {
            return Objects.equals(normalizedLeft, normalizedRight);
        }
        if (isValid(normalizedLeft) && isValid(normalizedRight)) {
            return compare(normalizedLeft, normalizedRight) == 0;
        }
        return normalizedLeft.equals(normalizedRight);
    }

    public static int compare(String left, String right) {
        int[] leftSegments = parseSegments(left);
        int[] rightSegments = parseSegments(right);
        for (int index = 0; index < leftSegments.length; index++) {
            int compared = Integer.compare(leftSegments[index], rightSegments[index]);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    public static Comparator<WorkflowVersion> byVersionDesc() {
        return (left, right) -> {
            int versionCompared = compare(right.getVersion(), left.getVersion());
            if (versionCompared != 0) {
                return versionCompared;
            }
            return Comparator.nullsLast(Comparator.<Long>naturalOrder()).compare(right.getId(), left.getId());
        };
    }

    public static String suggestNext(String latestVersion) {
        if (!isValid(latestVersion)) {
            return "1.0.0";
        }
        String normalized = normalize(latestVersion);
        String[] parts = normalized.split("\\.");
        if (parts.length == 1) {
            return String.valueOf(Integer.parseInt(parts[0]) + 1);
        }
        int[] segments = parseSegments(normalized);
        segments[2]++;
        return segments[0] + "." + segments[1] + "." + segments[2];
    }

    private static int[] parseSegments(String version) {
        int[] segments = new int[] {0, 0, 0};
        String normalized = normalize(version);
        if (normalized == null || !VERSION_PATTERN.matcher(normalized).matches()) {
            return segments;
        }
        String[] parts = normalized.split("\\.");
        for (int index = 0; index < parts.length && index < segments.length; index++) {
            segments[index] = Integer.parseInt(parts[index]);
        }
        return segments;
    }
}
