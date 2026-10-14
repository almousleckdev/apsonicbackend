package com.apsonic.backend.shared.exception;

import org.springframework.http.HttpStatus;

public class TokenException extends BaseException {

    public TokenException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
