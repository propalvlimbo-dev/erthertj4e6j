package ru.rooyzee.elytrixtrader.util;

import java.util.Locale;

/**
 * Отображаемые имена миров для сообщений и плейсхолдеров.
 */
public final class Worlds {

    private Worlds() {
    }

    public static String display(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }
        switch (name.toLowerCase(Locale.ROOT)) {
            case "world":
                return "РТП";
            case "world_nether":
                return "АД";
            case "world_the_end":
                return "ЭНД";
            default:
                return name;
        }
    }
}
