package com.twofourseven.dam.services;

import java.text.Normalizer;
import java.util.Locale;

/** Small string helpers shared by the services. */
final class Strings {

    private Strings() {
    }

    static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** "Café Photos!" -> "cafe-photos". Falls back to "item" if nothing usable is left. */
    static String slugify(String value) {
        String ascii = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "item" : slug;
    }
}
