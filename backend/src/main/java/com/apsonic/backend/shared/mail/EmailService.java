package com.apsonic.backend.shared.mail;

/** Sends email. Implementations throw {@link EmailDeliveryException} when the message cannot be handed off. */
@FunctionalInterface
public interface EmailService {

    void send(OutgoingEmail email);
}
