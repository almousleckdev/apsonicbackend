package com.apsonic.backend.shared.ratelimit;

import com.apsonic.backend.shared.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Per-client throttling of the endpoints that anonymous visitors can use to guess credentials, send mail-like
 * content or mutate counters. The client address is request.getRemoteAddr(), which Tomcat resolves from
 * X-Forwarded-For only when the immediate peer is a trusted proxy (server.forward-headers-strategy=native),
 * so the header cannot be spoofed by outside callers. Server-side rendering traffic is deliberately not limited.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    record Rule(String name, String method, Pattern path, int limit, Duration window) {
        boolean matches(HttpServletRequest request) {
            return method.equals(request.getMethod()) && path.matcher(request.getRequestURI()).matches();
        }
    }

    static final List<Rule> RULES = List.of(
            new Rule("login", "POST", Pattern.compile("/api/v1/auth/login"), 10, Duration.ofMinutes(1)),
            new Rule("refresh", "POST", Pattern.compile("/api/v1/auth/refresh"), 60, Duration.ofMinutes(1)),
            new Rule("contact", "POST", Pattern.compile("/api/v1/contact"), 5, Duration.ofMinutes(10)),
            new Rule("news-views", "POST", Pattern.compile("/api/v1/news/[^/]+/views"), 20, Duration.ofHours(1)));

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || matchingRule(request).isEmpty();
    }

    private Optional<Rule> matchingRule(HttpServletRequest request) {
        return RULES.stream().filter(rule -> rule.matches(request)).findFirst();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = matchingRule(request).orElseThrow();
        RateLimiter.Decision decision = rateLimiter.tryAcquire(rule.name(), request.getRemoteAddr(), rule.limit(), rule.window());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        log.warn("Rate limit '{}' exceeded by {}", rule.name(), request.getRemoteAddr());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                .message("Too many requests. Please try again later.")
                .path(request.getRequestURI())
                .build());
    }
}
