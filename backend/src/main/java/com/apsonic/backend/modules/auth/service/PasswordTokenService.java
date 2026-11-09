package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.entity.PasswordToken;
import com.apsonic.backend.modules.auth.entity.PasswordToken.Purpose;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.repository.PasswordTokenRepository;
import com.apsonic.backend.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Creates and spends the one-time links used to set a password. */
@Service
@RequiredArgsConstructor
public class PasswordTokenService {

    static final Duration INVITATION_VALIDITY = Duration.ofHours(24);
    static final Duration RESET_VALIDITY = Duration.ofHours(24);
    private static final int TOKEN_BYTES = 32;
    private static final Duration KEEP_EXPIRED_FOR = Duration.ofDays(7);

    private final PasswordTokenRepository repository;
    private final SecureRandom random = new SecureRandom();

    public static Duration validityOf(Purpose purpose) {
        return purpose == Purpose.INVITATION ? INVITATION_VALIDITY : RESET_VALIDITY;
    }

    /** Issues a new link for the user and cancels any earlier one. Returns the raw token; only its hash is stored. */
    @Transactional
    public String issue(User user, Purpose purpose) {
        Instant now = Instant.now();
        repository.invalidateOutstanding(user, now);
        repository.deleteByExpiresAtBefore(now.minus(KEEP_EXPIRED_FOR));

        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordToken token = new PasswordToken();
        token.setUser(user);
        token.setTokenHash(hash(raw));
        token.setPurpose(purpose);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(validityOf(purpose)));
        repository.save(token);
        return raw;
    }

    /** Spends the token and returns its user. Fails the same way for unknown, used and expired tokens. */
    @Transactional
    public User redeem(String rawToken) {
        String hash = hash(rawToken == null ? "" : rawToken);
        if (repository.markUsed(hash, Instant.now()) != 1) {
            throw new BadRequestException("This link is invalid or has expired");
        }
        return repository.findByTokenHash(hash).orElseThrow().getUser();
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
