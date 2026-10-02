package com.settl.backend.auth;

import com.settl.backend.auth.event.GroupInvitationEmailEvent;
import com.settl.backend.auth.event.VerificationEmailEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionalEmailServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TransactionalEmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new TransactionalEmailService(eventPublisher);
    }

    @Test
    void sendVerificationEmail_publishesVerificationEmailEvent() {
        emailService.sendVerificationEmail("test@example.com", "Test User", "https://settl.app/verify?token=xyz");

        ArgumentCaptor<VerificationEmailEvent> captor = ArgumentCaptor.forClass(VerificationEmailEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        VerificationEmailEvent event = captor.getValue();
        assertThat(event.toEmail()).isEqualTo("test@example.com");
        assertThat(event.displayName()).isEqualTo("Test User");
        assertThat(event.verificationUrl()).isEqualTo("https://settl.app/verify?token=xyz");
    }

    @Test
    void sendGroupInvitationEmail_publishesGroupInvitationEmailEvent() {
        emailService.sendGroupInvitationEmail("invite@example.com", "Alice", "Vacation", "https://settl.app/join?token=123", true);

        ArgumentCaptor<GroupInvitationEmailEvent> captor = ArgumentCaptor.forClass(GroupInvitationEmailEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        GroupInvitationEmailEvent event = captor.getValue();
        assertThat(event.toEmail()).isEqualTo("invite@example.com");
        assertThat(event.inviterName()).isEqualTo("Alice");
        assertThat(event.groupName()).isEqualTo("Vacation");
        assertThat(event.actionUrl()).isEqualTo("https://settl.app/join?token=123");
        assertThat(event.isNewUser()).isTrue();
    }
}
