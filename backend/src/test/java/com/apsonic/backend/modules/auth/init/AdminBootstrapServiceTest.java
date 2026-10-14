package com.apsonic.backend.modules.auth.init;

import com.apsonic.backend.BaseUnitTest;
import com.apsonic.backend.config.properties.BootstrapAdminProperties;
import com.apsonic.backend.modules.auth.entity.Role;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapServiceTest extends BaseUnitTest {

    /** Exact hash seeded for 'superadmin' by db/migration/V1__init_security_and_auth.sql (password: Admin@123). */
    private static final String V1_SEEDED_HASH = "$2a$10$jpoqFMV6HETV8JJC2ghcAehy8n/q.2j9kwDgR0gnl2RGW2mKIG7oa";

    private static final String STRONG_PASSWORD = "Str0ng!Bootstrap#2026";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final BootstrapAdminProperties properties = new BootstrapAdminProperties();
    private final Role superAdminRole = Role.builder().name("ROLE_SUPER_ADMIN").build();
    private AdminBootstrapService service;

    @BeforeEach
    void setUp() {
        service = new AdminBootstrapService(userRepository, encoder, properties);
    }

    private User user(String username, String hash, boolean enabled) {
        return User.builder().username(username).email(username + "@x.test").password(hash).enabled(enabled).build();
    }

    private void configureBootstrap(String password) {
        properties.setUsername("owner");
        properties.setEmail("owner@apsonic.africa");
        properties.setPassword(password);
    }

    @Test
    void v1SeededSuperAdminIsDisabledAndItsPasswordIsNeverReset() {
        User seeded = user("superadmin", V1_SEEDED_HASH, true);
        when(userRepository.findAll()).thenReturn(List.of(seeded));

        int disabled = service.disableAccountsWithDefaultPasswords();

        assertThat(disabled).isEqualTo(1);
        assertThat(seeded.isEnabled()).isFalse();
        assertThat(seeded.getPassword()).isEqualTo(V1_SEEDED_HASH);
        verify(userRepository).save(seeded);
    }

    @Test
    void accountWithUniquePasswordIsLeftUntouched() {
        User legit = user("editor", encoder.encode("Unique!Pass#99xyz"), true);
        when(userRepository.findAll()).thenReturn(List.of(legit));

        assertThat(service.disableAccountsWithDefaultPasswords()).isZero();

        assertThat(legit.isEnabled()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void createsFirstSuperAdminFromEnvironmentWithHashedPassword() {
        configureBootstrap(STRONG_PASSWORD);
        when(userRepository.existsByEnabledTrueAndRoles_Name("ROLE_SUPER_ADMIN")).thenReturn(false);

        service.createFirstSuperAdminIfRequired(superAdminRole);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("owner");
        assertThat(saved.getValue().getPassword()).isNotEqualTo(STRONG_PASSWORD);
        assertThat(encoder.matches(STRONG_PASSWORD, saved.getValue().getPassword())).isTrue();
        assertThat(saved.getValue().getRoles()).containsExactly(superAdminRole);
    }

    @Test
    void doesNothingWhenAnActiveSuperAdminAlreadyExists() {
        configureBootstrap(STRONG_PASSWORD);
        when(userRepository.existsByEnabledTrueAndRoles_Name("ROLE_SUPER_ADMIN")).thenReturn(true);

        service.createFirstSuperAdminIfRequired(superAdminRole);

        verify(userRepository, never()).save(any());
    }

    @Test
    void createsNoAccountWhenBootstrapIsNotConfigured() {
        when(userRepository.existsByEnabledTrueAndRoles_Name("ROLE_SUPER_ADMIN")).thenReturn(false);

        service.createFirstSuperAdminIfRequired(superAdminRole);

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsShortWeakAndKnownDefaultBootstrapPasswords() {
        when(userRepository.existsByEnabledTrueAndRoles_Name("ROLE_SUPER_ADMIN")).thenReturn(false);
        for (String bad : List.of("Admin@123", "Sh0rt!pw", "alllowercase-nodigits!!", "password")) {
            configureBootstrap(bad);
            assertThatThrownBy(() -> service.createFirstSuperAdminIfRequired(superAdminRole))
                    .isInstanceOf(IllegalStateException.class);
        }
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsBootstrapUsernameThatCollidesWithExistingAccount() {
        configureBootstrap(STRONG_PASSWORD);
        when(userRepository.existsByEnabledTrueAndRoles_Name("ROLE_SUPER_ADMIN")).thenReturn(false);
        when(userRepository.existsByUsername("owner")).thenReturn(true);

        assertThatThrownBy(() -> service.createFirstSuperAdminIfRequired(superAdminRole))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already belong");
    }
}
