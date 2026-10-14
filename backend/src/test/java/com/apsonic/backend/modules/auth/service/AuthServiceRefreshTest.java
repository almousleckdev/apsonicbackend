package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.BaseUnitTest;
import com.apsonic.backend.config.properties.JwtProperties;
import com.apsonic.backend.modules.auth.dto.response.TokenRefreshResponse;
import com.apsonic.backend.modules.auth.entity.RefreshToken;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.repository.RefreshTokenRepository;
import com.apsonic.backend.modules.auth.service.impl.AuthServiceImpl;
import com.apsonic.backend.security.jwt.JwtTokenProvider;
import com.apsonic.backend.shared.exception.TokenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceRefreshTest extends BaseUnitTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String RAW = "raw-refresh-token-value";
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().username("editor").email("e@x.test").password("h").enabled(true).build();
        user.setId(7L);
    }

    private RefreshToken stored(boolean revoked, Instant expiry, Instant updatedAt) {
        RefreshToken t = RefreshToken.builder().user(user).token(RefreshTokenCodec.hash(RAW))
                .expiryDate(expiry).revoked(revoked).build();
        t.setUpdatedAt(updatedAt);
        return t;
    }

    @Test
    void rotatesTokenAndStoresOnlyTheNewHash() {
        RefreshToken current = stored(false, Instant.now().plusSeconds(600), Instant.now());
        when(refreshTokenRepository.findByToken(RefreshTokenCodec.hash(RAW))).thenReturn(Optional.of(current));
        when(tokenProvider.generateAccessTokenFromUserPrincipal(any())).thenReturn("access");
        when(jwtProperties.getExpirationMs()).thenReturn(900_000L);
        when(jwtProperties.getRefreshExpirationMs()).thenReturn(604_800_000L);

        AuthenticationResult<TokenRefreshResponse> result = authService.refreshToken(RAW);

        assertThat(current.isRevoked()).isTrue();
        assertThat(result.refreshToken()).isNotEqualTo(RAW);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getToken()).isEqualTo(RefreshTokenCodec.hash(result.refreshToken()));
    }

    @Test
    void unknownTokenIsRejected() {
        when(refreshTokenRepository.findByToken(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.refreshToken("nope")).isInstanceOf(TokenException.class);
    }

    @Test
    void reuseOfRotatedTokenRevokesEverySessionOfTheUser() {
        RefreshToken replayed = stored(true, Instant.now().plusSeconds(600), Instant.now().minusSeconds(3600));
        when(refreshTokenRepository.findByToken(RefreshTokenCodec.hash(RAW))).thenReturn(Optional.of(replayed));

        assertThatThrownBy(() -> authService.refreshToken(RAW)).isInstanceOf(TokenException.class);

        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    void reuseInsideGraceWindowIsRejectedWithoutKillingOtherSessions() {
        RefreshToken justRotated = stored(true, Instant.now().plusSeconds(600), Instant.now().minusSeconds(2));
        when(refreshTokenRepository.findByToken(RefreshTokenCodec.hash(RAW))).thenReturn(Optional.of(justRotated));

        assertThatThrownBy(() -> authService.refreshToken(RAW)).isInstanceOf(TokenException.class);

        verify(refreshTokenRepository, never()).deleteByUser(any());
    }

    @Test
    void disabledUserCannotRefreshAndLosesAllSessions() {
        user.setEnabled(false);
        RefreshToken current = stored(false, Instant.now().plusSeconds(600), Instant.now());
        when(refreshTokenRepository.findByToken(RefreshTokenCodec.hash(RAW))).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> authService.refreshToken(RAW)).isInstanceOf(TokenException.class);

        verify(refreshTokenRepository).deleteByUser(user);
        verify(tokenProvider, never()).generateAccessTokenFromUserPrincipal(any());
    }

    @Test
    void lockedUserCannotRefresh() {
        user.setAccountNonLocked(false);
        RefreshToken current = stored(false, Instant.now().plusSeconds(600), Instant.now());
        when(refreshTokenRepository.findByToken(RefreshTokenCodec.hash(RAW))).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> authService.refreshToken(RAW)).isInstanceOf(TokenException.class);
    }

    @Test
    void expiredTokenIsRejectedAndDeleted() {
        RefreshToken expired = stored(false, Instant.now().minusSeconds(5), Instant.now().minusSeconds(100));
        when(refreshTokenRepository.findByToken(RefreshTokenCodec.hash(RAW))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refreshToken(RAW)).isInstanceOf(TokenException.class);

        verify(refreshTokenRepository).delete(expired);
    }
}
