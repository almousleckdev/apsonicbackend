package com.apsonic.backend.security.jwt;

import com.apsonic.backend.security.service.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                Claims claims = tokenProvider.parseClaims(jwt);

                if (tokenBlacklistService.isTokenBlacklisted(claims.getId())) {
                    log.warn("Attempt to use a revoked token");
                } else {
                    Number userId = claims.get("id", Number.class);
                    UserDetails userDetails = userId != null
                            ? customUserDetailsService.loadUserById(userId.longValue())
                            : customUserDetailsService.loadUserByUsername(claims.getSubject());

                    // A valid signature is not enough: deactivated or locked accounts lose access immediately.
                    if (isUsable(userDetails)) {
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.warn("Rejected token for disabled, locked or expired account '{}'", userDetails.getUsername());
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Could not set user authentication in security context: {}", ex.getClass().getSimpleName());
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isUsable(UserDetails user) {
        return user.isEnabled() && user.isAccountNonLocked()
                && user.isAccountNonExpired() && user.isCredentialsNonExpired();
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
