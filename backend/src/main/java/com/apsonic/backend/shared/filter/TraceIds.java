package com.apsonic.backend.shared.filter;

import org.slf4j.MDC;

import java.util.regex.Pattern;

/** The reference of the request being handled, safe to show to a visitor (it can come from a request header). */
public final class TraceIds {

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    private TraceIds() {
    }

    /** The trace id of the current request, or null when there is none or it is not a plain token. */
    public static String current() {
        String id = MDC.get(TraceIdFilter.MDC_TRACE_ID_KEY);
        return id != null && SAFE.matcher(id).matches() ? id : null;
    }
}
