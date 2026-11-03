package com.apsonic.backend.shared.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Used until mail is configured. Never logs the message: it contains a one-time link. */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledEmailService implements EmailService {

    @Override
    public void send(OutgoingEmail email) {
        log.warn("Email is not configured (app.mail.enabled=false); a message was not sent");
        throw new EmailDeliveryException("Email sending is not configured on the server");
    }
}
