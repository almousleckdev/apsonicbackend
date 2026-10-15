package com.apsonic.backend.shared.exception;

import org.springframework.http.HttpStatus;

/** The request is well formed but conflicts with the current state (stale version, invalid transition, ...). */
public class ConflictException extends BaseException {
    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
