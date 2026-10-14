package com.apsonic.backend.modules.auth.service;

import com.apsonic.backend.modules.auth.dto.request.CreateUserRequest;
import com.apsonic.backend.modules.auth.dto.request.UpdateUserRequest;
import com.apsonic.backend.modules.auth.dto.response.RoleResponse;
import com.apsonic.backend.modules.auth.dto.response.UserManagementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    Page<UserManagementResponse> getUsersList(String query, String roleName, Pageable pageable);
    UserManagementResponse createUser(CreateUserRequest request);
    UserManagementResponse updateUser(Long id, UpdateUserRequest request);
    UserManagementResponse toggleUserStatus(Long id, Boolean enabled);
    void deleteUser(Long id);
    List<RoleResponse> getAllRoles();
}
