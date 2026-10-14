package com.apsonic.backend.modules.auth.controller;

import com.apsonic.backend.modules.auth.dto.request.ChangePasswordRequest;
import com.apsonic.backend.modules.auth.dto.request.LoginRequest;
import com.apsonic.backend.modules.auth.dto.request.UpdateProfileRequest;
import com.apsonic.backend.modules.auth.dto.response.AuthResponse;
import com.apsonic.backend.modules.auth.dto.response.TokenRefreshResponse;
import com.apsonic.backend.modules.auth.dto.response.UserProfileResponse;
import com.apsonic.backend.modules.auth.service.AuthService;
import com.apsonic.backend.modules.auth.service.AuthenticationResult;
import com.apsonic.backend.security.UserPrincipal;
import com.apsonic.backend.shared.api.ApiResponse;
import com.apsonic.backend.shared.cookie.RefreshCookieFactory;
import com.apsonic.backend.shared.exception.TokenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.apsonic.backend.security.jwt.TokenBlacklistService;
import com.apsonic.backend.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication Management", description = "Endpoints for user authentication, token management, and profile access")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory refreshCookieFactory;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    @Operation(summary = "User Login", description = "Authenticates a user with username/email and password. Returns a short-lived access token in the body and delivers the refresh token exclusively via an httpOnly cookie")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletResponse response) {
        AuthenticationResult<AuthResponse> result = authService.login(loginRequest);
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Authentication successful", result.response()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT Token", description = "Generates a new access token using the refresh token carried in the httpOnly cookie, and rotates that cookie")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(
            @CookieValue(name = "${app.security.cookie.name}", required = false) String refreshTokenCookie,
            HttpServletResponse response) {
        if (refreshTokenCookie == null || refreshTokenCookie.isBlank()) {
            throw new TokenException("Refresh token cookie is missing");
        }

        AuthenticationResult<TokenRefreshResponse> result = authService.refreshToken(refreshTokenCookie);
        setRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", result.response()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get Current User Profile", description = "Returns profile details of the currently authenticated user")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser) {
        UserProfileResponse userProfile = authService.getCurrentUserProfile(currentUser);
        return ResponseEntity.ok(ApiResponse.success("User profile retrieved successfully", userProfile));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update Current User Profile", description = "Allows logged-in user or admin to update their own profile picture, username, email, names, and password")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody UpdateProfileRequest request) {
        UserProfileResponse userProfile = authService.updateProfile(currentUser, request);
        return ResponseEntity.ok(ApiResponse.success("User profile updated successfully", userProfile));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change Password", description = "Allows current authenticated user to update their password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(currentUser, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout User", description = "Revokes the refresh token identified by the httpOnly cookie and clears it. Does not require a valid access token, since its purpose is precisely to end the session")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "${app.security.cookie.name}", required = false) String refreshTokenCookie,
            HttpServletRequest request,
            HttpServletResponse response) {
        
        authService.logout(refreshTokenCookie);
        
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String jwt = bearerToken.substring(7);
            try {
                if (tokenProvider.validateToken(jwt)) {
                    java.util.Date expiration = tokenProvider.getExpirationFromToken(jwt);
                    tokenBlacklistService.blacklistToken(jwt, expiration.getTime());
                }
            } catch (Exception e) {
                // Ignore validation exception for expired token on logout
            }
        }

        response.addHeader(HttpHeaders.SET_COOKIE,
                refreshCookieFactory.clear().toString());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    // Helper method.
    private void setRefreshCookie(HttpServletResponse response, String refreshTokenValue) {
        response.addHeader(HttpHeaders
                        .SET_COOKIE,
                refreshCookieFactory
                .create(refreshTokenValue).toString());
    }
}
