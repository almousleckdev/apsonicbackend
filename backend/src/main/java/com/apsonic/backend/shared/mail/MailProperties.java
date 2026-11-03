package com.apsonic.backend.shared.mail;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Outgoing mail settings. Disabled until an SMTP account is configured (spring.mail.* and app.mail.from). */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    private boolean enabled = false;
    /** The sender address, for example noreply@apsonic-moto.com. */
    private String from = "";
    /** Shown next to the sender address in the recipient's inbox. */
    private String fromName = "APSONIC";
}
