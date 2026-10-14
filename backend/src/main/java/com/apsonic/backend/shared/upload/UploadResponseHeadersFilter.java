package com.apsonic.backend.shared.upload;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Makes everything under /uploads inert even if a hostile file ever reaches disk: no MIME sniffing,
 * and a sandboxing CSP that blocks script execution should the file be opened as a document.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadResponseHeadersFilter extends OncePerRequestFilter {

    static final String UPLOADS_PREFIX = "/uploads/";
    static final String CSP = "default-src 'none'; img-src 'self' data:; media-src 'self'; style-src 'unsafe-inline'; sandbox";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(UPLOADS_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("Cross-Origin-Resource-Policy", "same-site");
        chain.doFilter(request, response);
    }
}
