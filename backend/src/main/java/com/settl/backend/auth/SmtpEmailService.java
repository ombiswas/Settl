package com.settl.backend.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@settl.app}")
    private String fromEmail;

    public SmtpEmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    @Override
    public void sendVerificationEmail(String toEmail, String displayName, String verificationUrl) {
        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Verification link for {} ({}): {}", displayName, toEmail, verificationUrl);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Verify your email address — Settl");

            String htmlBody = buildVerificationEmailHtml(displayName, verificationUrl);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Verification email successfully dispatched to {}", toEmail);
        } catch (MessagingException | RuntimeException ex) {
            log.error("Failed to send verification email to {}. Fallback link: {}", toEmail, verificationUrl, ex);
        }
    }

    private String buildVerificationEmailHtml(String displayName, String verificationUrl) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>Verify your email - Settl</title>
              <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; color: #0f172a; margin: 0; padding: 0; }
                .container { max-width: 580px; margin: 40px auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); }
                .header { background-color: #0f172a; color: #ffffff; padding: 32px 40px; text-align: center; }
                .header h1 { margin: 0; font-size: 26px; font-weight: 700; letter-spacing: -0.03em; }
                .header p { margin: 6px 0 0 0; font-size: 14px; color: #94a3b8; }
                .content { padding: 40px; }
                .greeting { font-size: 18px; font-weight: 600; margin-bottom: 12px; color: #0f172a; }
                .text { font-size: 15px; line-height: 1.6; color: #475569; margin-bottom: 24px; }
                .btn-container { text-align: center; margin: 32px 0; }
                .btn { display: inline-block; background-color: #2563eb; color: #ffffff !important; padding: 14px 32px; font-size: 15px; font-weight: 600; text-decoration: none; border-radius: 8px; }
                .notice { font-size: 13px; color: #64748b; line-height: 1.5; padding: 12px 16px; background-color: #f1f5f9; border-radius: 6px; margin-top: 24px; }
                .alt-link { font-size: 13px; color: #64748b; word-break: break-all; margin-top: 20px; }
                .footer { background-color: #f8fafc; padding: 24px 40px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>Settl</h1>
                  <p>Smart Expense Splitting & Debt Simplification</p>
                </div>
                <div class="content">
                  <div class="greeting">Welcome, %s!</div>
                  <div class="text">
                    Thank you for signing up for Settl. Please confirm your email address to activate your account and start tracking and splitting expenses.
                  </div>
                  <div class="btn-container">
                    <a href="%s" class="btn" target="_blank">Verify Email Address</a>
                  </div>
                  <div class="notice">
                    <strong>Security Notice:</strong> This verification link will expire in <strong>24 hours</strong>. If you did not create a Settl account, you can safely ignore this email.
                  </div>
                  <div class="alt-link">
                    If the button doesn't work, copy and paste this link into your browser:<br>
                    <a href="%s" style="color: #2563eb;">%s</a>
                  </div>
                </div>
                <div class="footer">
                  &copy; Settl App. All rights reserved.
                </div>
              </div>
            </body>
            </html>
            """.formatted(displayName, verificationUrl, verificationUrl, verificationUrl);
    }
}
