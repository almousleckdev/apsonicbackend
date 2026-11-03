package com.apsonic.backend.modules.auth.dto.request;

import com.apsonic.backend.shared.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "The link is missing its token")
    private String token;

    @NotBlank(message = "Password is required")
    @ValidPassword
    private String password;
}
