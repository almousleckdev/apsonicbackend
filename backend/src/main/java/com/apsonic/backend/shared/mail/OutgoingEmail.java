package com.apsonic.backend.shared.mail;

/** One message to one recipient. {@code html} is optional; every message has a plain-text version. */
public record OutgoingEmail(String to, String subject, String text, String html) {

    public OutgoingEmail(String to, String subject, String text) {
        this(to, subject, text, null);
    }
}
