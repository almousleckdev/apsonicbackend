package com.apsonic.backend.security;

import com.apsonic.backend.config.properties.CorsProperties;
import com.apsonic.backend.shared.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

/**
 * CSRF defence for endpoints authenticated by the refresh cookie (/auth/refresh, /auth/logout).
 * SameSite=Strict is the first layer; this adds two independent checks that do not depend on browser
 * cookie behaviour: a custom header that cross-site pages cannot send without a CORS preflight (which our
 * CORS policy denies), and an allow-list check of the Origin header.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CookieAuthRequestGuardFilter extends OncePerRequestFilter {

    public static final String REQUIRED_HEADER = "X-Requested-With";
    public static final String REQUIRED_HEADER_VALUE = "apsonic-web";

    private static final Set<String> GUARDED_PATHS = Set.of("/api/v1/auth/refresh", "/api/v1/auth/logout");

    private final CorsProperties corsProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !GUARDED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        boolean headerOk = REQUIRED_HEADER_VALUE.equals(request.getHeader(REQUIRED_HEADER));
        boolean originOk = origin == null || corsProperties.getAllowedOrigins().contains(origin);

        if (headerOk && originOk) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Blocked cookie-authenticated request to {} (header ok: {}, origin ok: {})",
                request.getRequestURI(), headerOk, originOk);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message("Cross-site request blocked")
                .path(request.getRequestURI())
                .build());
    }
}
