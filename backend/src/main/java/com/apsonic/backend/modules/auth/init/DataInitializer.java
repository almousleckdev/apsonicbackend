package com.apsonic.backend.modules.auth.init;

import com.apsonic.backend.modules.auth.entity.Role;
import com.apsonic.backend.modules.auth.entity.User;
import com.apsonic.backend.modules.auth.repository.PermissionRepository;
import com.apsonic.backend.modules.auth.repository.RoleRepository;
import com.apsonic.backend.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        // Initialize default roles if missing
        Role superAdminRole = roleRepository.findByName("ROLE_SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_SUPER_ADMIN")
                        .description("Super administrator with full system permissions")
                        .permissions(new HashSet<>(permissionRepository.findAll()))
                        .build()));

        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_ADMIN")
                        .description("System administrator with management access")
                        .permissions(new HashSet<>())
                        .build()));

        Role editorRole = roleRepository.findByName("ROLE_EDITOR")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_EDITOR")
                        .description("Content editor with article publishing permissions")
                        .permissions(new HashSet<>())
                        .build()));

        if (userRepository.count() == 0) {
            log.info("No users found in database. Initializing default Super Admin user...");

            User superAdmin = User.builder()
                    .email("admin@apsonic.com")
                    .username("superadmin")
                    .password(passwordEncoder.encode("Admin@123"))
                    .firstName("System")
                    .lastName("Administrator")
                    .enabled(true)
                    .accountNonExpired(true)
                    .credentialsNonExpired(true)
                    .accountNonLocked(true)
                    .roles(Set.of(superAdminRole))
                    .build();

            userRepository.save(superAdmin);
            log.info("Default Super Admin initialized with username 'superadmin' and email 'admin@apsonic.com'");
        } else {
            userRepository.findByUsername("superadmin").ifPresent(superadmin -> {
                superadmin.setPassword(passwordEncoder.encode("Admin@123"));
                userRepository.save(superadmin);
                log.info("Super Admin password forcefully reset to Admin@123 for Gatling tests.");
            });
        }
    }
}
