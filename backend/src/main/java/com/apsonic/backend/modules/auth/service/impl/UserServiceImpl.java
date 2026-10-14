package com.apsonic.backend.modules.auth.service.impl;

import com.apsonic.backend.modules.auth.dto.request.CreateUserRequest;
import com.apsonic.backend.modules.auth.dto.request.UpdateUserRequest;
import com.apsonic.backend.modules.auth.dto.response.RoleResponse;
import com.apsonic.backend.modules.auth.dto.response.UserManagementResponse;
import com.apsonic.backend.modules.auth.entity.Permission;
import com.apsonic.backend.modules.auth.entity.Role;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.repository.RoleRepository;
import com.apsonic.backend.modules.auth.repository.UserRepository;
import com.apsonic.backend.modules.auth.service.UserService;
import com.apsonic.backend.shared.exception.DuplicateResourceException;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public Page<UserManagementResponse> getUsersList(String query, String roleName, Pageable pageable) {
        Page<User> users;
        if (query != null && !query.trim().isEmpty()) {
            String search = query.trim().toLowerCase();
            users = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }
        return users.map(this::toUserManagementResponse);
    }

    @Override
    @Transactional
    public UserManagementResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        Set<Role> assignedRoles = resolveRoles(request.getRoles());

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .avatarUrl(request.getAvatarUrl())
                .roles(assignedRoles)
                .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                .build();

        User saved = userRepository.save(user);
        log.info("Created user account with ID {}", saved.getId());
        return toUserManagementResponse(saved);
    }

    @Override
    @Transactional
    public UserManagementResponse updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("User", "email", request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            Set<Role> updatedRoles = resolveRoles(request.getRoles());
            user.setRoles(updatedRoles);
        }

        User updated = userRepository.save(user);
        log.info("Updated user account ID {}", updated.getId());
        return toUserManagementResponse(updated);
    }

    @Override
    @Transactional
    public UserManagementResponse toggleUserStatus(Long id, Boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        boolean targetStatus = enabled != null ? enabled : !user.isEnabled();
        user.setEnabled(targetStatus);
        User updated = userRepository.save(user);
        log.info("Toggled user status for ID {} to {}", id, targetStatus);
        return toUserManagementResponse(updated);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setDeleted(true);
        user.setDeletedAt(java.time.Instant.now());
        user.setDeletedBy(com.apsonic.backend.shared.util.SecurityUtils.getCurrentUsername().orElse("system"));
        userRepository.save(user);
        log.info("Soft deleted user account ID {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(role -> RoleResponse.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .description(role.getDescription())
                        .permissions(role.getPermissions() != null
                                ? role.getPermissions().stream().map(Permission::getName).toList()
                                : List.of())
                        .build())
                .toList();
    }

    private Set<Role> resolveRoles(List<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        if (roleNames == null || roleNames.isEmpty()) {
            roleNames = List.of("ROLE_ADMIN");
        }

        for (String rName : roleNames) {
            String fullRole = rName.startsWith("ROLE_") ? rName : "ROLE_" + rName;
            Role role = roleRepository.findByName(fullRole)
                    .orElseThrow(() -> new ResourceNotFoundException("Role", "name", fullRole));
            roles.add(role);
        }
        return roles;
    }

    private UserManagementResponse toUserManagementResponse(User user) {
        List<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream()
                .filter(r -> r != null && r.getName() != null)
                .map(Role::getName)
                .toList()
                : List.of();

        return UserManagementResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .enabled(user.isEnabled())
                .roles(roleNames)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
