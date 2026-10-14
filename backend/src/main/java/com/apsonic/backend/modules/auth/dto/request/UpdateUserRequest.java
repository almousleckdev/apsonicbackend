package com.apsonic.backend.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    private String username;

    @Email(message = "Invalid email format")
    private String email;

    private String firstName;
    private String lastName;
    private String avatarUrl;
    private String password; // Optional new password
    private Boolean enabled;
    private List<String> roles;
}
