package ru.rooyzee.elytrixtrader.util;

import java.util.Locale;

public final class Numbers {

    private Numbers() {
    }

    public static String amount(long value) {
        return String.format(Locale.US, "%,d", value).replace(',', ' ');
    }

    public static String amount(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.01D) {
            return amount((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.2f", value).replace(',', ' ');
    }

    public static String duration(long seconds) {
        long safe = Math.max(0L, seconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        long rest = safe % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%dч %02dм", hours, minutes);
        }
        if (minutes > 0L) {
            return String.format(Locale.US, "%dм %02dс", minutes, rest);
        }
        return rest + "с";
    }

    public static double parseDouble(String input, double fallback) {
        if (input == null) {
            return fallback;
        }
        String value = input.trim().replace(" ", "").replace(",", ".");
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public static int parseSeconds(String input, int fallback) {
        if (input == null || input.trim().isEmpty()) {
            return fallback;
        }
        String value = input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        try {
            if (value.endsWith("d")) {
                return (int) (Double.parseDouble(value.substring(0, value.length() - 1)) * 86400.0D);
            }
            if (value.endsWith("h")) {
                return (int) (Double.parseDouble(value.substring(0, value.length() - 1)) * 3600.0D);
            }
            if (value.endsWith("m")) {
                return (int) (Double.parseDouble(value.substring(0, value.length() - 1)) * 60.0D);
            }
            if (value.endsWith("s")) {
                return (int) Double.parseDouble(value.substring(0, value.length() - 1));
            }
            if (value.contains(":")) {
                String[] parts = value.split(":");
                int total = 0;
                for (String part : parts) {
                    total = total * 60 + Integer.parseInt(part);
                }
                return total;
            }
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
