package com.apsonic.backend.security.service;

import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.repository.UserRepository;
import com.apsonic.backend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private static final long LOCK_TIME_DURATION_MINUTES = 30;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found with username or email: " + usernameOrEmail
                ));

        // Unlock account automatically after 30 minutes
        if (!user.isAccountNonLocked() && user.getLockTime() != null) {
            if (Duration.between(user.getLockTime(), Instant.now()).toMinutes() >= LOCK_TIME_DURATION_MINUTES) {
                user.setAccountNonLocked(true);
                user.setLockTime(null);
                user.setFailedLoginAttempts(0);
                userRepository.save(user);
            }
        }

        return UserPrincipal.create(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));

        return UserPrincipal.create(user);
    }
}
