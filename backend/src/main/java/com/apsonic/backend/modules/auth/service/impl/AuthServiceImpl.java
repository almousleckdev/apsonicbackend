package com.apsonic.backend.modules.auth.service.impl;

import com.apsonic.backend.config.properties.JwtProperties;
import com.apsonic.backend.modules.auth.dto.request.ChangePasswordRequest;
import com.apsonic.backend.modules.auth.dto.request.LoginRequest;
import com.apsonic.backend.modules.auth.dto.request.UpdateProfileRequest;
import com.apsonic.backend.modules.auth.dto.response.AuthResponse;
import com.apsonic.backend.modules.auth.dto.response.TokenRefreshResponse;
import com.apsonic.backend.modules.auth.dto.response.UserProfileResponse;
import com.apsonic.backend.modules.auth.entity.RefreshToken;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.mapper.AuthMapper;
import com.apsonic.backend.modules.auth.repository.RefreshTokenRepository;
import com.apsonic.backend.modules.auth.repository.UserRepository;
import com.apsonic.backend.modules.auth.service.AuthService;
import com.apsonic.backend.modules.auth.service.RefreshTokenCodec;
import com.apsonic.backend.modules.auth.service.AuthenticationResult;
import com.apsonic.backend.security.UserPrincipal;
import com.apsonic.backend.security.jwt.JwtTokenProvider;
import com.apsonic.backend.shared.exception.BadRequestException;
import com.apsonic.backend.shared.exception.DuplicateResourceException;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import com.apsonic.backend.shared.exception.TokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthMapper authMapper;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long REUSE_GRACE_SECONDS = 10;

    @Override
    @Transactional
    public AuthenticationResult<AuthResponse> login(LoginRequest loginRequest) {
        String identifier = loginRequest.getUsernameOrEmail().trim();
        log.debug("Attempting authentication");

        Optional<User> userOpt = userRepository.findByUsernameOrEmail(identifier, identifier);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.isAccountNonLocked()) {
                throw new LockedException("Your account is locked due to 5 consecutive failed login attempts. Please try again after 30 minutes.");
            }
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, loginRequest.getPassword())
            );
        } catch (BadCredentialsException ex) {
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                int attempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(attempts);
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    user.setAccountNonLocked(false);
                    user.setLockTime(Instant.now());
                    userRepository.save(user);
                    log.warn("Account id {} locked after {} failed attempts", user.getId(), attempts);
                    throw new LockedException("Your account has been locked for 30 minutes due to 5 failed login attempts.");
                } else {
                    userRepository.save(user);
                    log.warn("Failed login attempt {}/{} for account id {}", attempts, MAX_FAILED_ATTEMPTS, user.getId());
                }
            }
            throw ex;
        }

        // On successful authentication, reset failed attempts
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getFailedLoginAttempts() > 0) {
                user.setFailedLoginAttempts(0);
                user.setLockTime(null);
                userRepository.save(user);
            }
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        String accessToken = tokenProvider.generateAccessToken(authentication);
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        String refreshToken = issueRefreshToken(user);

        UserProfileResponse userProfile = authMapper.toUserProfileResponse(user);

        AuthResponse response = AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpirationMs() / 1000)
                .user(userProfile)
                .build();

        return new AuthenticationResult<>(response, refreshToken);
    }

    @Override
    @Transactional(noRollbackFor = TokenException.class)
    public AuthenticationResult<TokenRefreshResponse> refreshToken(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByToken(RefreshTokenCodec.hash(refreshTokenValue))
                .orElseThrow(() -> new TokenException("Refresh token was not found or is revoked"));

        if (stored.isRevoked()) {
            // A rotated token was presented again. Within the grace window this is a benign race between two
            // tabs; otherwise it indicates theft, so every session of that user is ended.
            boolean benignRace = stored.getUpdatedAt() != null
                    && stored.getUpdatedAt().isAfter(Instant.now().minusSeconds(REUSE_GRACE_SECONDS));
            if (!benignRace) {
                log.warn("Refresh token reuse detected for user id {}; revoking all sessions", stored.getUser().getId());
                refreshTokenRepository.deleteByUser(stored.getUser());
            }
            throw new TokenException("Refresh token was expired or revoked. Please log in again.");
        }
        if (stored.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            throw new TokenException("Refresh token was expired or revoked. Please log in again.");
        }

        User user = stored.getUser();
        if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()
                || !user.isCredentialsNonExpired()) {
            refreshTokenRepository.deleteByUser(user);
            throw new TokenException("Account is not active. Please contact an administrator.");
        }

        stored.setRevoked(true);
        try {
            refreshTokenRepository.saveAndFlush(stored);
        } catch (org.springframework.dao.OptimisticLockingFailureException concurrentRotation) {
            throw new TokenException("Refresh token was already used. Please retry.");
        }

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String newAccessToken = tokenProvider.generateAccessTokenFromUserPrincipal(userPrincipal);
        String newRefreshToken = issueRefreshToken(user);

        TokenRefreshResponse response = TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpirationMs() / 1000)
                .build();
        return new AuthenticationResult<>(response, newRefreshToken);
    }

    @Override
    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenRepository.findByToken(RefreshTokenCodec.hash(refreshTokenValue))
                    .ifPresent(refreshTokenRepository::delete);
        }
        SecurityContextHolder.clearContext();
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UserPrincipal currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUser.getId()));
        return authMapper.toUserProfileResponse(user);
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UserPrincipal currentUser, UpdateProfileRequest request) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUser.getId()));

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("User", "email", request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        User updatedUser = userRepository.save(user);
        return authMapper.toUserProfileResponse(updatedUser);
    }

    @Override
    @Transactional
    public void changePassword(UserPrincipal currentUser, ChangePasswordRequest request) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUser.getId()));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
    }

    /** Persists only the hash; the raw value is returned once so it can be set in the httpOnly cookie. */
    private String issueRefreshToken(User user) {
        String raw = RefreshTokenCodec.newRawToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(RefreshTokenCodec.hash(raw))
                .expiryDate(Instant.now().plusMillis(jwtProperties.getRefreshExpirationMs()))
                .revoked(false)
                .build());
        return raw;
    }
}
