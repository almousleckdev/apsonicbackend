package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.entity.PasswordToken.Purpose;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.repository.RefreshTokenRepository;
import com.apsonic.backend.modules.auth.repository.UserRepository;
import com.apsonic.backend.shared.exception.BadRequestException;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import com.apsonic.backend.shared.mail.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.RejectedExecutionException;

/** Forgotten passwords and account invitations: sends the one-time link, and turns it into a new password. */
@Slf4j
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordTokenService tokenService;
    private final PasswordEmailComposer composer;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final TaskExecutor mailExecutor;

    public PasswordResetService(UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                PasswordTokenService tokenService,
                                PasswordEmailComposer composer,
                                EmailService emailService,
                                PasswordEncoder passwordEncoder,
                                @Qualifier("mailTaskExecutor") TaskExecutor mailExecutor) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.composer = composer;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.mailExecutor = mailExecutor;
    }

    /**
     * Handles "forgot password". Returns immediately and does the work in the background, so the response is the same
     * and just as fast whether or not the address belongs to an account.
     */
    public void requestReset(String email) {
        try {
            mailExecutor.execute(() -> sendResetIfAccountExists(email));
        } catch (RejectedExecutionException e) {
            log.warn("Password reset request dropped: the mail queue is full");
        }
    }

    private void sendResetIfAccountExists(String email) {
        try {
            userRepository.findFirstByEmailIgnoreCase(email.trim())
                    .filter(User::isEnabled)
                    .ifPresent(user -> emailService.send(composer.compose(user, Purpose.RESET, tokenService.issue(user, Purpose.RESET))));
        } catch (RuntimeException e) {
            log.warn("Could not send a password reset email: {}", e.getClass().getSimpleName());
        }
    }

    /** Sends (or re-sends) the invitation to choose a password. Throws if the email cannot be handed to the mail server. */
    public void sendInvitation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (!user.isEnabled()) {
            throw new BadRequestException("The account is suspended. Activate it before sending an invitation.");
        }
        if (!user.isInvitationPending()) {
            throw new BadRequestException("This person has already set a password. Use \"forgot password\" to reset it.");
        }
        emailService.send(composer.compose(user, Purpose.INVITATION, tokenService.issue(user, Purpose.INVITATION)));
    }

    /** Sets the password chosen on the link's page. The link is spent, other links are cancelled, and every session ends. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = tokenService.redeem(rawToken);
        if (!user.isEnabled()) {
            throw new BadRequestException("This link is invalid or has expired");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setInvitationPending(false);
        // A new password does not lift a block for wrong passwords: only the 30 minutes running out, or an
        // administrator, does. Otherwise anyone with access to the mailbox could skip the block.
        if (!LoginLockPolicy.isLocked(user, java.time.Instant.now())) {
            user.setFailedLoginAttempts(0);
            user.setLockTime(null);
            user.setAccountNonLocked(true);
        }
        userRepository.save(user);
        refreshTokenRepository.deleteByUser(user);
        log.info("Password set through a one-time link for user ID {}", user.getId());
    }
}
