package com.apsonic.backend.modules.auth.dto.request;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String username;

    @Email(message = "Invalid email format")
    private String email;

    private String avatarUrl;
    private String currentPassword;
    private String newPassword;
}
