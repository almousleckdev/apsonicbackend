package com.apsonic.backend.modules.auth.init;

import com.apsonic.backend.config.properties.BootstrapAdminProperties;
import com.apsonic.backend.modules.auth.entity.Role;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Handles the two security-critical start-up concerns around administrator accounts:
 * disabling accounts that still use publicly known default passwords, and creating the first
 * Super Admin from operator-supplied environment configuration. It never changes the password
 * of an existing account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBootstrapService {

    static final String SUPER_ADMIN_ROLE = "ROLE_SUPER_ADMIN";
    static final int MIN_PASSWORD_LENGTH = 12;

    /** Passwords that appeared in this repository's seed data, scripts or tests. Treated as compromised. */
    private static final List<String> KNOWN_DEFAULT_PASSWORDS = List.of("Admin@123", "password");

    private static final Pattern COMPLEXITY = Pattern.compile(
            "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!._\\-])(?=\\S+$).+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties properties;

    /** Disables every account whose stored hash matches a known default password. Returns how many. */
    public int disableAccountsWithDefaultPasswords() {
        int disabled = 0;
        for (User user : userRepository.findAll()) {
            if (user.isEnabled() && usesKnownDefaultPassword(user)) {
                user.setEnabled(false);
                userRepository.save(user);
                disabled++;
                log.warn("Account '{}' still used a known default password and has been DISABLED. "
                        + "Set BOOTSTRAP_ADMIN_* to create a replacement administrator.", user.getUsername());
            }
        }
        return disabled;
    }

    /** Creates the first Super Admin if none is active and bootstrap credentials were supplied. */
    public void createFirstSuperAdminIfRequired(Role superAdminRole) {
        if (userRepository.existsByEnabledTrueAndRoles_Name(SUPER_ADMIN_ROLE)) {
            return;
        }
        if (!properties.isConfigured()) {
            log.warn("No active Super Admin exists and BOOTSTRAP_ADMIN_USERNAME/EMAIL/PASSWORD are not set. "
                    + "Nobody will be able to sign in to the admin area.");
            return;
        }
        validate(properties);

        User admin = User.builder()
                .username(properties.getUsername().trim())
                .email(properties.getEmail().trim())
                .password(passwordEncoder.encode(properties.getPassword()))
                .firstName("System")
                .lastName("Administrator")
                .enabled(true)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .accountNonLocked(true)
                .roles(Set.of(superAdminRole))
                .build();
        userRepository.save(admin);
        log.info("Bootstrap Super Admin '{}' created. Remove BOOTSTRAP_ADMIN_PASSWORD from the environment now.",
                admin.getUsername());
    }

    private void validate(BootstrapAdminProperties p) {
        String password = p.getPassword();
        if (password.length() < MIN_PASSWORD_LENGTH || !COMPLEXITY.matcher(password).matches()) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH
                    + " characters with upper, lower, digit and special character");
        }
        if (KNOWN_DEFAULT_PASSWORDS.contains(password)) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must not be a known default password");
        }
        if (userRepository.existsByUsername(p.getUsername().trim()) || userRepository.existsByEmail(p.getEmail().trim())) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_USERNAME/EMAIL already belong to an existing account; choose different values");
        }
    }

    private boolean usesKnownDefaultPassword(User user) {
        return KNOWN_DEFAULT_PASSWORDS.stream().anyMatch(known -> passwordEncoder.matches(known, user.getPassword()));
    }
}
