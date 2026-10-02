package com.settl.backend.auth;

import com.settl.backend.auth.event.GroupInvitationEmailEvent;
import com.settl.backend.auth.event.VerificationEmailEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Primary implementation of {@link EmailService} that publishes application events.
 * The actual email dispatching occurs asynchronously after transaction commit via
 * {@link EmailEventListener}.
 */
@Service
@Primary
public class TransactionalEmailService implements EmailService {

    private final ApplicationEventPublisher eventPublisher;

    public TransactionalEmailService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String displayName, String verificationUrl) {
        eventPublisher.publishEvent(new VerificationEmailEvent(toEmail, displayName, verificationUrl));
    }

    @Override
    public void sendGroupInvitationEmail(String toEmail, String inviterName, String groupName, String actionUrl, boolean isNewUser) {
        eventPublisher.publishEvent(new GroupInvitationEmailEvent(toEmail, inviterName, groupName, actionUrl, isNewUser));
    }
}
