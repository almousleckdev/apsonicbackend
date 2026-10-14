package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.dto.request.CreateUserRequest;
import com.apsonic.backend.modules.auth.dto.response.UserManagementResponse;
import com.apsonic.backend.modules.auth.entity.Role;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.repository.RoleRepository;
import com.apsonic.backend.modules.auth.repository.UserRepository;
import com.apsonic.backend.modules.auth.service.impl.UserServiceImpl;
import com.apsonic.backend.shared.exception.DuplicateResourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private com.apsonic.backend.modules.auth.repository.RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        adminRole = Role.builder().name("ROLE_ADMIN").description("Admin").build();
        adminRole.setId(1L);

        sampleUser = User.builder()
                .username("testadmin")
                .email("testadmin@apsonic.com")
                .password("encoded_pass")
                .firstName("Test")
                .lastName("Admin")
                .enabled(true)
                .roles(new HashSet<>(List.of(adminRole)))
                .build();
        sampleUser.setId(10L);
    }

    @Test
    @DisplayName("Super Admin should successfully create a user with roles")
    void createUser_Success() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("newadmin")
                .email("newadmin@apsonic.com")
                .password("Admin@123")
                .firstName("New")
                .lastName("Admin")
                .roles(List.of("ADMIN"))
                .build();

        when(userRepository.existsByUsername("newadmin")).thenReturn(false);
        when(userRepository.existsByEmail("newadmin@apsonic.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("Admin@123")).thenReturn("hashed_pass");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(20L);
            return u;
        });

        UserManagementResponse response = userService.createUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getUsername()).isEqualTo("newadmin");
        assertThat(response.getRoles()).contains("ROLE_ADMIN");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when username already exists")
    void createUser_DuplicateUsername() {
        CreateUserRequest request = CreateUserRequest.builder()
                .username("testadmin")
                .email("new@apsonic.com")
                .password("pass")
                .roles(List.of("ADMIN"))
                .build();

        when(userRepository.existsByUsername("testadmin")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("username");
    }

    @Test
    @DisplayName("Should toggle user status successfully")
    void toggleUserStatus_Success() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserManagementResponse response = userService.toggleUserStatus(10L, false);

        assertThat(response).isNotNull();
        assertThat(response.getEnabled()).isFalse();
    }
}
