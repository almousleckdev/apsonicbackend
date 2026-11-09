package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.entity.User;

import java.time.Instant;

/** What the admin list shows for an account. A person who has not opened their invitation is not "active". */
public enum UserStatus {
    ACTIVE,
    /** Invitation sent and its link still works. */
    INVITED,
    /** Invitation sent but the link has expired (or was never sent): the administrator must send a new one. */
    INVITATION_EXPIRED,
    /** Blocked by an administrator. */
    SUSPENDED,
    /** Locked after repeated failed sign-ins. */
    LOCKED;

    /** @param invitationExpiresAt expiry of the newest link sent to the account, or null if there is none */
    public static UserStatus of(User user, Instant invitationExpiresAt, Instant now) {
        if (!user.isEnabled()) {
            return SUSPENDED;
        }
        if (!user.isAccountNonLocked()) {
            return LOCKED;
        }
        if (user.isInvitationPending()) {
            return invitationExpiresAt != null && invitationExpiresAt.isAfter(now) ? INVITED : INVITATION_EXPIRED;
        }
        return ACTIVE;
    }
}
