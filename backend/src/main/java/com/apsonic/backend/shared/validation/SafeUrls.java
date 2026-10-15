package com.apsonic.backend.shared.validation;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * URL rules for CMS-authored values. Everything is an allow-list: anything not matched is rejected, so schemes such as
 * javascript:, data: and vbscript: - and protocol-relative //host values - can never be stored.
 */
public final class SafeUrls {

    private static final int MAX_LENGTH = 2048;
    private static final Pattern SITE_PATH = Pattern.compile("^/(?!/)[A-Za-z0-9\\-._~!$&'()*+,;=:@%/?#\\[\\]]*$");
    private static final Pattern ANCHOR = Pattern.compile("^#[A-Za-z0-9\\-_:.]{1,100}$");
    private static final Pattern UPLOAD_PATH = Pattern.compile("^/uploads/[A-Za-z0-9\\-._~%/]+$");
    private static final Pattern MAIL = Pattern.compile("^mailto:[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}(\\?[A-Za-z0-9=&%+._\\-]*)?$");
    private static final Pattern PHONE = Pattern.compile("^tel:\\+?[0-9][0-9\\-. ()]{3,30}$");

    private SafeUrls() {
    }

    /** A link a visitor can follow: site path, #anchor, http(s) URL, mailto: or tel:. */
    public static boolean isSafeLink(String value) {
        if (!basicallyValid(value)) {
            return false;
        }
        if (SITE_PATH.matcher(value).matches() && !hasTraversal(value)) {
            return true;
        }
        return ANCHOR.matcher(value).matches()
                || MAIL.matcher(value).matches()
                || PHONE.matcher(value).matches()
                || isHttpUrl(value);
    }

    /** An absolute http(s) URL with a host and no embedded credentials. */
    public static boolean isHttpUrl(String value) {
        if (!basicallyValid(value)) {
            return false;
        }
        String lower = value.toLowerCase();
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return uri.getHost() != null && !uri.getHost().isBlank() && uri.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Same as {@link #isHttpUrl} but https only (canonical and social URLs). */
    public static boolean isHttpsUrl(String value) {
        return isHttpUrl(value) && value.toLowerCase().startsWith("https://");
    }

    /** A file served by this site (/uploads/...) or an absolute http(s) URL. */
    public static boolean isSafeMedia(String value) {
        if (!basicallyValid(value)) {
            return false;
        }
        if (value.startsWith("/uploads/")) {
            return UPLOAD_PATH.matcher(value).matches() && !hasTraversal(value);
        }
        if (value.startsWith("/") && !value.startsWith("//")) {
            return SITE_PATH.matcher(value).matches() && !hasTraversal(value);
        }
        return isHttpUrl(value);
    }

    private static boolean basicallyValid(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x20 || c == 0x7f || c == '\\' || c == '<' || c == '>' || c == '"') {
                return false;
            }
        }
        return true;
    }

    private static boolean hasTraversal(String value) {
        String lower = value.toLowerCase();
        return lower.contains("/../") || lower.endsWith("/..") || lower.contains("%2e%2e") || lower.contains("%2f")
                || lower.contains("%5c");
    }
}
