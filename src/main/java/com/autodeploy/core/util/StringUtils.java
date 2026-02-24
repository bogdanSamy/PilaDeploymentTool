package com.autodeploy.core.util;

/**
 * Utilitar pentru operații comune pe String-uri.
 * Folosit de model-uri, validări, și servicii.
 */
public final class StringUtils {

    private StringUtils() {}

    /**
     * Verifică dacă un string este non-null și conține cel puțin un caracter non-whitespace.
     */
    public static boolean isNotEmpty(String str) {
        return str != null && !str.trim().isEmpty();
    }

    /**
     * Verifică dacă un string este null, gol, sau conține doar whitespace.
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Returnează valoarea sau un fallback dacă e goală.
     */
    public static String defaultIfEmpty(String value, String defaultValue) {
        return isEmpty(value) ? defaultValue : value;
    }
}