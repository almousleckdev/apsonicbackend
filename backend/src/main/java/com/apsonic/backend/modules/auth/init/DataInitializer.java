package com.apsonic.backend.modules.auth.init;

import com.apsonic.backend.modules.auth.entity.Role;
import com.apsonic.backend.modules.auth.repository.PermissionRepository;
import com.apsonic.backend.modules.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AdminBootstrapService adminBootstrapService;

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

        adminBootstrapService.disableAccountsWithDefaultPasswords();
        adminBootstrapService.createFirstSuperAdminIfRequired(superAdminRole);
    }
}
