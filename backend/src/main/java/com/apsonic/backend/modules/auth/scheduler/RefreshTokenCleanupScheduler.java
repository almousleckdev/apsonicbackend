package com.apsonic.backend.modules.auth.scheduler;

import com.apsonic.backend.modules.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Removes expired refresh tokens (rotated ones are kept until expiry so reuse can be detected). */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
        log.info("Purged {} expired refresh tokens", removed);
    }
}
