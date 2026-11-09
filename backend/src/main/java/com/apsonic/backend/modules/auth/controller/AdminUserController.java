package com.apsonic.backend.modules.auth.controller;

import com.apsonic.backend.modules.auth.dto.request.CreateUserRequest;
import com.apsonic.backend.modules.auth.dto.request.UpdateUserRequest;
import com.apsonic.backend.modules.auth.dto.response.RoleResponse;
import com.apsonic.backend.modules.auth.dto.response.UserManagementResponse;
import com.apsonic.backend.modules.auth.service.PasswordResetService;
import com.apsonic.backend.modules.auth.service.UserProvisioningService;
import com.apsonic.backend.modules.auth.service.UserService;
import com.apsonic.backend.shared.api.ApiResponse;
import com.apsonic.backend.shared.api.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminUserController {

    private final UserService userService;
    private final UserProvisioningService userProvisioningService;
    private final PasswordResetService passwordResetService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserManagementResponse>>> getUsersList(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserManagementResponse> result = userService.getUsersList(query, role, pageable);
        return ResponseEntity.ok(ApiResponse.success("Users list retrieved successfully", PageResponse.from(result)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserManagementResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserManagementResponse response = userProvisioningService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully with assigned roles", response));
    }

    /** Lifts a block caused by repeated wrong passwords. Administrators and super administrators only (class-level rule). */
    @PostMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<UserManagementResponse>> unlockUser(@PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(ApiResponse.success("Account unlocked", userService.unlockUser(id)));
    }

    @PostMapping("/{id}/invitation")
    public ResponseEntity<ApiResponse<Void>> resendInvitation(@PathVariable(name = "id") Long id) {
        passwordResetService.sendInvitation(id);
        return ResponseEntity.ok(ApiResponse.success("Invitation email sent", null));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserManagementResponse>> updateUser(
            @PathVariable(name = "id") Long id,
            @RequestBody UpdateUserRequest request
    ) {
        UserManagementResponse response = userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("User details updated successfully", response));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UserManagementResponse>> toggleUserStatus(
            @PathVariable(name = "id") Long id,
            @RequestParam(name = "enabled", required = false) Boolean enabled
    ) {
        UserManagementResponse response = userService.toggleUserStatus(id, enabled);
        return ResponseEntity.ok(ApiResponse.success("User status updated successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable(name = "id") Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = userService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success("Roles and permissions list retrieved", roles));
    }
}
