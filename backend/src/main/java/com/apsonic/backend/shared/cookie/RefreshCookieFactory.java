package com.apsonic.backend.shared.cookie;

import com.apsonic.backend.config.properties.AuthCookieProperties;
import com.apsonic.backend.config.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

    private final AuthCookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public ResponseCookie create(String refreshTokenValue) {
        return build(refreshTokenValue, Duration.ofMillis(jwtProperties.getRefreshExpirationMs()));
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    //helper method
    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(cookieProperties.getName(), value)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite(cookieProperties.getSameSite())
                .path(cookieProperties.getPath())
                .maxAge(maxAge)
                .build();
    }
}
