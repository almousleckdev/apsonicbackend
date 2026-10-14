package com.apsonic.backend.modules.news.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public class NewsSlugHelper {

    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");

    public static String generateSlug(String input) {
        if (input == null || input.isBlank()) {
            return "news-" + UUID.randomUUID().toString().substring(0, 8);
        }

        String nowhitespace = WHITESPACE.matcher(input.trim()).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = slug.toLowerCase(Locale.ENGLISH);
        slug = slug.replaceAll("-+", "-").replaceAll("^-|-$", "");

        if (slug.isBlank()) {
            return "news-" + UUID.randomUUID().toString().substring(0, 8);
        }

        return slug;
    }

    public static String appendUniqueSuffix(String baseSlug) {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return baseSlug + "-" + suffix;
    }
}
