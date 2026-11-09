package com.apsonic.backend.modules.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserManagementResponse {
    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String avatarUrl;
    private Boolean enabled;
    /** ACTIVE, INVITED, INVITATION_EXPIRED, SUSPENDED or LOCKED (see UserStatus). */
    private String status;
    /** When the newest invitation or reset link stops working; set while the account is still invited. */
    private Instant invitationExpiresAt;
    /** While LOCKED: when the block ends by itself (an administrator can lift it earlier). */
    private Instant lockedUntil;
    private List<String> roles;
    private Instant createdAt;
    private Instant updatedAt;
    /** Only set when the account was created without a password: whether the invitation email was sent. */
    private Boolean invitationSent;
}
