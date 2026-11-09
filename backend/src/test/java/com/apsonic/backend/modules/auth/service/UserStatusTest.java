package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserStatusTest {

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

    private static User user() {
        return User.builder().username("u").email("u@x.test").password("h").build();
    }

    @Test
    void anOrdinaryAccountIsActive() {
        assertThat(UserStatus.of(user(), null, NOW)).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void anInvitedAccountIsNotActiveWhileTheLinkWorks() {
        User invited = user();
        invited.setInvitationPending(true);
        assertThat(UserStatus.of(invited, NOW.plusSeconds(3600), NOW)).isEqualTo(UserStatus.INVITED);
    }

    @Test
    void anInvitationThatExpiredOrWasNeverSentNeedsANewOne() {
        User invited = user();
        invited.setInvitationPending(true);
        assertThat(UserStatus.of(invited, NOW.minusSeconds(1), NOW)).isEqualTo(UserStatus.INVITATION_EXPIRED);
        assertThat(UserStatus.of(invited, null, NOW)).isEqualTo(UserStatus.INVITATION_EXPIRED);
    }

    @Test
    void suspendedAndLockedWinOverEverythingElse() {
        User invited = user();
        invited.setInvitationPending(true);
        invited.setAccountNonLocked(false);
        assertThat(UserStatus.of(invited, NOW.plusSeconds(60), NOW)).isEqualTo(UserStatus.LOCKED);
        invited.setEnabled(false);
        assertThat(UserStatus.of(invited, NOW.plusSeconds(60), NOW)).isEqualTo(UserStatus.SUSPENDED);
    }
}
