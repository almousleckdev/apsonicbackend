package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.entity.User;

import java.time.Duration;
import java.time.Instant;

/** Five wrong passwords in a row block an account for 30 minutes; an administrator can lift the block earlier. */
public final class LoginLockPolicy {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private LoginLockPolicy() {
    }

    /** True while the block is in force. A block with no recorded start (older data) lasts until an administrator lifts it. */
    public static boolean isLocked(User user, Instant now) {
        if (user.isAccountNonLocked()) {
            return false;
        }
        Instant start = user.getLockTime();
        return start == null || start.plus(LOCK_DURATION).isAfter(now);
    }

    /** When the block ends by itself, or null if it has no end (only an administrator can lift it). */
    public static Instant lockedUntil(User user) {
        return user.getLockTime() == null ? null : user.getLockTime().plus(LOCK_DURATION);
    }

    public static String blockedMessage(User user, Instant now) {
        Instant until = lockedUntil(user);
        if (until == null) {
            return "Your account is locked after " + MAX_FAILED_ATTEMPTS
                    + " failed sign-ins. Please ask an administrator to unlock it.";
        }
        long minutes = Math.max(1, (Duration.between(now, until).getSeconds() + 59) / 60);
        return "Your account is locked after " + MAX_FAILED_ATTEMPTS + " failed sign-ins. Try again in " + minutes
                + (minutes == 1 ? " minute" : " minutes") + ", or ask an administrator to unlock it.";
    }

    /** Wording when the account has just been blocked by the latest wrong password. */
    public static String justBlockedMessage() {
        return "Your account is locked for " + LOCK_DURATION.toMinutes() + " minutes after " + MAX_FAILED_ATTEMPTS
                + " failed sign-ins. Ask an administrator to unlock it sooner.";
    }
}
