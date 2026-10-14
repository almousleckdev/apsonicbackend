package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.BaseUnitTest;
import com.apsonic.backend.config.properties.JwtProperties;
import com.apsonic.backend.modules.auth.dto.request.LoginRequest;
import com.apsonic.backend.modules.auth.dto.response.AuthResponse;
import com.apsonic.backend.modules.auth.dto.response.TokenRefreshResponse;
import com.apsonic.backend.modules.auth.dto.response.UserProfileResponse;
import com.apsonic.backend.modules.auth.entity.RefreshToken;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.mapper.AuthMapper;
import com.apsonic.backend.modules.auth.repository.RefreshTokenRepository;
import com.apsonic.backend.modules.auth.repository.UserRepository;
import com.apsonic.backend.modules.auth.service.impl.AuthServiceImpl;
import com.apsonic.backend.security.UserPrincipal;
import com.apsonic.backend.security.jwt.JwtTokenProvider;
import com.apsonic.backend.shared.exception.TokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

class AuthServiceTest extends BaseUnitTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthServiceImpl authService;

    private User user;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("admin@apsonic.com")
                .username("superadmin")
                .password("encoded_password")
                .enabled(true)
                .build();
        user.setId(1L);

        userPrincipal = UserPrincipal.builder()
                .id(1L)
                .username("superadmin")
                .email("admin@apsonic.com")
                .authorities(Collections.emptyList())
                .build();
    }

    @Test
    @DisplayName("Should successfully authenticate user and return tokens")
    void login_Success() {
        // Given
        LoginRequest loginRequest = new LoginRequest("superadmin", "Admin@123");
        String fakeToken = "jwt_access_token";
        String fakeRefreshTokenString = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(fakeRefreshTokenString)
                .expiryDate(Instant.now().plusSeconds(604800))
                .build();

        given(authenticationManager.authenticate(any())).willReturn(authentication);
        given(authentication.getPrincipal()).willReturn(userPrincipal);
        given(tokenProvider.generateAccessToken(authentication)).willReturn(fakeToken);
        given(jwtProperties.getExpirationMs()).willReturn(86400000L);
        given(jwtProperties.getRefreshExpirationMs()).willReturn(604800000L);
        given(userRepository.findByUsernameOrEmail("superadmin", "superadmin")).willReturn(Optional.of(user));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(authMapper.toUserProfileResponse(user)).willReturn(
                UserProfileResponse.builder()
                        .id(1L)
                        .username("superadmin")
                        .email("admin@apsonic.com")
                        .build()
        );

        // When
        AuthenticationResult<AuthResponse> result = authService.login(loginRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.response().getAccessToken()).isEqualTo(fakeToken);
        assertThat(result.response().getUser().getUsername()).isEqualTo("superadmin");
        // The refresh token must travel out-of-band (httpOnly cookie), never on the response body.
        org.mockito.ArgumentCaptor<RefreshToken> saved = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        org.mockito.Mockito.verify(refreshTokenRepository).save(saved.capture());
        assertThat(result.refreshToken()).isNotBlank();
        // Only the digest is persisted - never the value that goes into the cookie.
        assertThat(saved.getValue().getToken()).isNotEqualTo(result.refreshToken())
                .isEqualTo(com.apsonic.backend.modules.auth.service.RefreshTokenCodec.hash(result.refreshToken()));
    }

    @Test
    @DisplayName("Should reject an unknown or already-rotated refresh token")
    void refreshToken_UnknownToken_Throws() {
        given(refreshTokenRepository.findByToken(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken("does-not-exist"))
                .isInstanceOf(TokenException.class);
    }
}
