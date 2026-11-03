package com.apsonic.backend.shared.mail;

import com.apsonic.backend.shared.exception.BaseException;
import org.springframework.http.HttpStatus;

public class EmailDeliveryException extends BaseException {

    public EmailDeliveryException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
