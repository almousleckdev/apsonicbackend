package com.apsonic.backend.modules.auth.controller;

import com.apsonic.backend.modules.auth.dto.request.ForgotPasswordRequest;
import com.apsonic.backend.modules.auth.dto.request.ResetPasswordRequest;
import com.apsonic.backend.modules.auth.service.PasswordResetService;
import com.apsonic.backend.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Password reset", description = "Forgotten passwords and account invitations (public, by one-time link)")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /** Always answers the same way, so the response never reveals which email addresses have an account. */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("If an account exists for that address, an email with a link has been sent", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.getToken(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success("Password set successfully", null));
    }
}
