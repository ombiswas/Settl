package com.settl.backend.auth;

import com.settl.backend.auth.event.GroupInvitationEmailEvent;
import com.settl.backend.auth.event.VerificationEmailEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailEventListenerTest {

    @Mock
    private SmtpEmailService smtpEmailService;

    private EmailEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new EmailEventListener(smtpEmailService);
    }

    @Test
    void extractDomain_handlesVariousFormats() {
        assertThat(EmailEventListener.extractDomain("alice@example.com")).isEqualTo("example.com");
        assertThat(EmailEventListener.extractDomain("bob.smith+tag@mail.sub.company.org")).isEqualTo("mail.sub.company.org");
        assertThat(EmailEventListener.extractDomain(null)).isEqualTo("unknown");
        assertThat(EmailEventListener.extractDomain("")).isEqualTo("unknown");
        assertThat(EmailEventListener.extractDomain("no-at-symbol")).isEqualTo("unknown");
        assertThat(EmailEventListener.extractDomain("user@")).isEqualTo("unknown");
        assertThat(EmailEventListener.extractDomain("@domain.com")).isEqualTo("domain.com");
    }

    @Test
    void handleVerificationEmail_dispatchesToSmtpEmailService() {
        VerificationEmailEvent event = new VerificationEmailEvent("user@test.org", "User Name", "https://settl.app/verify?token=123");

        listener.handleVerificationEmail(event);

        verify(smtpEmailService).sendVerificationEmail(
                eq("user@test.org"),
                eq("User Name"),
                eq("https://settl.app/verify?token=123")
        );
    }

    @Test
    void handleVerificationEmail_catchesAndLogsSmtpFailuresWithoutThrowing() {
        VerificationEmailEvent event = new VerificationEmailEvent("user@test.org", "User Name", "https://settl.app/verify?token=123");
        doThrow(new MailSendException("SMTP timeout")).when(smtpEmailService)
                .sendVerificationEmail(anyString(), anyString(), anyString());

        assertThatCode(() -> listener.handleVerificationEmail(event))
                .doesNotThrowAnyException();
    }

    @Test
    void handleGroupInvitationEmail_dispatchesToSmtpEmailService() {
        GroupInvitationEmailEvent event = new GroupInvitationEmailEvent(
                "invitee@example.com",
                "Inviter",
                "Ski Trip",
                "https://settl.app/join?token=abc",
                true
        );

        listener.handleGroupInvitationEmail(event);

        verify(smtpEmailService).sendGroupInvitationEmail(
                eq("invitee@example.com"),
                eq("Inviter"),
                eq("Ski Trip"),
                eq("https://settl.app/join?token=abc"),
                eq(true)
        );
    }

    @Test
    void handleGroupInvitationEmail_catchesAndLogsSmtpFailuresWithoutThrowing() {
        GroupInvitationEmailEvent event = new GroupInvitationEmailEvent(
                "invitee@example.com",
                "Inviter",
                "Ski Trip",
                "https://settl.app/join?token=abc",
                false
        );
        doThrow(new RuntimeException("Mail server down")).when(smtpEmailService)
                .sendGroupInvitationEmail(any(), any(), any(), any(), anyBoolean());

        assertThatCode(() -> listener.handleGroupInvitationEmail(event))
                .doesNotThrowAnyException();
    }
}
