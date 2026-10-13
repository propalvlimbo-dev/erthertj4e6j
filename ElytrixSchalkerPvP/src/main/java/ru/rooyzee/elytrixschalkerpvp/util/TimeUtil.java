package ru.rooyzee.elytrixschalkerpvp.util;

public class TimeUtil {

    public static String formatSeconds(long totalSeconds) {
        if (totalSeconds <= 0) return "0 сек.";
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0) {
            return minutes + " мин. " + seconds + " сек.";
        }
        return seconds + " сек.";
    }
}