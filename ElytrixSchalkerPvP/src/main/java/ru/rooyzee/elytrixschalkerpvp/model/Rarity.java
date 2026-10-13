package ru.rooyzee.elytrixschalkerpvp.model;

public enum Rarity {
    COMMON,
    RARE,
    MYTHICAL,
    LEGENDARY;

    public static Rarity fromString(String str) {
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}