package com.dynamicportal.util;

/**
 * Small helper methods for converting and formatting time spans.
 */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static long daysToMillis(long days) {
        return days * 24L * 60L * 60L * 1000L;
    }

    /**
     * Formats a millisecond duration as e.g. "2d 14h 32m".
     * Returns a friendly message if the duration is zero or negative.
     */
    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "Any moment now...";
        }

        long totalMinutes = millis / (60L * 1000L);
        long days = totalMinutes / (60L * 24L);
        long hours = (totalMinutes / 60L) % 24L;
        long minutes = totalMinutes % 60L;

        return days + "d " + hours + "h " + minutes + "m";
    }
}
