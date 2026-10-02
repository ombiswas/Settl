package com.settl.backend.auth;

import com.settl.backend.auth.event.GroupInvitationEmailEvent;
import com.settl.backend.auth.event.VerificationEmailEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener that dispatches emails asynchronously after transaction commit.
 * Catches all delivery exceptions and logs recipient domain and email type without
 * leaking tokens or full URLs.
 */
@Component
public class EmailEventListener {

    private static final Logger log = LoggerFactory.getLogger(EmailEventListener.class);

    private final SmtpEmailService smtpEmailService;

    public EmailEventListener(SmtpEmailService smtpEmailService) {
        this.smtpEmailService = smtpEmailService;
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleVerificationEmail(VerificationEmailEvent event) {
        try {
            smtpEmailService.sendVerificationEmail(event.toEmail(), event.displayName(), event.verificationUrl());
        } catch (Exception ex) {
            String domain = extractDomain(event.toEmail());
            log.error("Failed to send VERIFICATION email to recipient domain [{}]: {}", domain, ex.getMessage(), ex);
        }
    }

    @Async("mailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleGroupInvitationEmail(GroupInvitationEmailEvent event) {
        try {
            smtpEmailService.sendGroupInvitationEmail(
                    event.toEmail(),
                    event.inviterName(),
                    event.groupName(),
                    event.actionUrl(),
                    event.isNewUser()
            );
        } catch (Exception ex) {
            String domain = extractDomain(event.toEmail());
            log.error("Failed to send GROUP_INVITATION email to recipient domain [{}]: {}", domain, ex.getMessage(), ex);
        }
    }

    public static String extractDomain(String email) {
        if (email == null) {
            return "unknown";
        }
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex >= email.length() - 1) {
            return "unknown";
        }
        String domain = email.substring(atIndex + 1).trim().toLowerCase();
        return domain.isEmpty() ? "unknown" : domain;
    }
}
