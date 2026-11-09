package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.dto.request.CreateUserRequest;
import com.apsonic.backend.modules.auth.dto.response.UserManagementResponse;
import com.apsonic.backend.shared.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/** Creates accounts for the admin UI: with an email invitation when no password is given. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProvisioningService {

    private static final int UNUSABLE_PASSWORD_BYTES = 48;

    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final SecureRandom random = new SecureRandom();

    public UserManagementResponse create(CreateUserRequest request) {
        boolean invite = request.getPassword() == null || request.getPassword().isBlank();
        if (invite) {
            // Nobody knows this value; the person sets a real password from the invitation link.
            request.setPassword(unusablePassword());
        }
        UserManagementResponse created = userService.createUser(request);
        if (!invite) {
            return created;
        }
        userService.markInvitationPending(created.getId());
        Boolean sent = Boolean.TRUE.equals(created.getEnabled()) ? invitationSent(created.getId()) : null;
        // Read the account again so its status reflects the invitation that was just issued.
        UserManagementResponse fresh = userService.getUser(created.getId());
        fresh.setInvitationSent(sent);
        return fresh;
    }

    private boolean invitationSent(Long userId) {
        try {
            passwordResetService.sendInvitation(userId);
            return true;
        } catch (BaseException e) {
            log.warn("Account {} was created but its invitation email was not sent: {}", userId, e.getMessage());
            return false;
        }
    }

    private String unusablePassword() {
        byte[] bytes = new byte[UNUSABLE_PASSWORD_BYTES];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
