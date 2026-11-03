package com.apsonic.backend.shared.mail;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailServiceTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final MailProperties properties = new MailProperties();
    private final SmtpEmailService service = new SmtpEmailService(sender, properties);

    SmtpEmailServiceTest() {
        properties.setEnabled(true);
        properties.setFrom("noreply@apsonic-moto.com");
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendsAChineseMessageFromTheConfiguredSender() throws Exception {
        service.send(new OutgoingEmail("someone@example.com", "APSONIC 管理后台账号邀请", "您好", "<p>您好</p>"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getSubject()).isEqualTo("APSONIC 管理后台账号邀请");
        assertThat(sent.getValue().getFrom()[0].toString()).contains("noreply@apsonic-moto.com");
        assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("someone@example.com");
    }

    @Test
    void aFailingMailServerBecomesAnEmailDeliveryException() {
        doThrow(new MailSendException("smtp down")).when(sender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.send(new OutgoingEmail("someone@example.com", "主题", "正文")))
                .isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    void theDisabledServiceRefusesToSend() {
        assertThatThrownBy(() -> new DisabledEmailService().send(new OutgoingEmail("a@b.c", "主题", "正文")))
                .isInstanceOf(EmailDeliveryException.class);
    }
}
