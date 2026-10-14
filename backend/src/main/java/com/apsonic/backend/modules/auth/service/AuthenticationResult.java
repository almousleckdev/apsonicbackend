package com.apsonic.backend.modules.auth.service;

public record AuthenticationResult<T>(T response, String refreshToken) {
}
