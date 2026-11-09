package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Keeps count of wrong passwords. Every method commits on its own: a failed sign-in ends in an exception, which would
 * roll back the counter if it shared the sign-in's transaction (the block would then never take effect). The counter is
 * changed with single UPDATE statements so parallel guesses cannot be undercounted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;

    /** Ends a block that has run its 30 minutes, and refuses the sign-in while a block is still in force. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = LockedException.class)
    public void checkNotLocked(String identifier) {
        Instant now = Instant.now();
        Optional<User> found = userRepository.findByUsernameOrEmail(identifier, identifier);
        if (found.isEmpty() || found.get().isAccountNonLocked()) {
            return;
        }
        User user = found.get();
        if (LoginLockPolicy.isLocked(user, now)) {
            throw new LockedException(LoginLockPolicy.blockedMessage(user, now));
        }
        userRepository.unlock(user.getId());
        log.info("The 30-minute block on account id {} has ended", user.getId());
    }

    /**
     * Counts one wrong password for the account, if there is one, and blocks it at the fifth.
     *
     * @return the message to show when the account is now blocked, or empty while attempts remain
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<String> recordFailure(String identifier) {
        Optional<User> found = userRepository.findByUsernameOrEmail(identifier, identifier);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Long id = found.get().getId();
        userRepository.incrementFailedAttempts(id);
        int attempts = userRepository.findFailedAttempts(id);
        if (attempts >= LoginLockPolicy.MAX_FAILED_ATTEMPTS) {
            if (userRepository.lockAccount(id, Instant.now()) == 1) {
                log.warn("Account id {} blocked for {} minutes after {} failed sign-ins", id,
                        LoginLockPolicy.LOCK_DURATION.toMinutes(), attempts);
            }
            return Optional.of(LoginLockPolicy.justBlockedMessage());
        }
        log.warn("Failed sign-in {}/{} for account id {}", attempts, LoginLockPolicy.MAX_FAILED_ATTEMPTS, id);
        return Optional.empty();
    }

    /** A correct password: the count of wrong ones starts again. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearFailures(Long userId) {
        userRepository.clearFailedAttempts(userId);
    }
}
