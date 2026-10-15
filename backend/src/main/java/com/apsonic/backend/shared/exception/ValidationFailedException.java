package com.apsonic.backend.shared.exception;

import com.apsonic.backend.shared.api.ValidationError;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/** The request body is well formed JSON but its content breaks the rules; carries every problem found. */
@Getter
public class ValidationFailedException extends BaseException {

    private final List<ValidationError> errors;

    public ValidationFailedException(String message, List<ValidationError> errors) {
        super(message, HttpStatus.BAD_REQUEST);
        this.errors = errors;
    }
}
