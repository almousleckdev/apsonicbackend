package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.dto.request.ChangePasswordRequest;
import com.apsonic.backend.modules.auth.dto.request.LoginRequest;
import com.apsonic.backend.modules.auth.dto.request.UpdateProfileRequest;
import com.apsonic.backend.modules.auth.dto.response.AuthResponse;
import com.apsonic.backend.modules.auth.dto.response.TokenRefreshResponse;
import com.apsonic.backend.modules.auth.dto.response.UserProfileResponse;
import com.apsonic.backend.security.UserPrincipal;

public interface AuthService {

    AuthenticationResult<AuthResponse> login(LoginRequest loginRequest);

    AuthenticationResult<TokenRefreshResponse> refreshToken(String refreshTokenValue);

    UserProfileResponse getCurrentUserProfile(UserPrincipal currentUser);

    UserProfileResponse updateProfile(UserPrincipal currentUser, UpdateProfileRequest request);

    void changePassword(UserPrincipal currentUser, ChangePasswordRequest request);

    void logout(String refreshToken);
}
