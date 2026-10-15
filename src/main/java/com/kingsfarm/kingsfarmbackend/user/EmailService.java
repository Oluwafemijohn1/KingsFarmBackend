package com.kingsfarm.kingsfarmbackend.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text account emails over SMTP. If MAIL_HOST is blank (local dev
 * or SMTP not set up yet) nothing is sent and the link is written to the
 * server log instead, so the flow stays testable. Never throws: a mail
 * failure must not roll back creating the user - the admin can resend.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String from;
    private final String frontendBaseUrl;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${spring.mail.host:}") String host,
                        @Value("${app.mail.from}") String from,
                        @Value("${app.mail.frontend-base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.host = host;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl.replaceAll("/+$", "");
    }

    public String linkFor(String rawToken) {
        return frontendBaseUrl + "/verify-email?token=" + rawToken;
    }

    /** @return true if the email was handed to the SMTP server. */
    public boolean sendAccountSetup(User user, String rawToken, boolean passwordReset, int validHours) {
        String link = linkFor(rawToken);
        String subject = passwordReset ? "Reset your King's Farm password" : "Verify your email for King's Farm";
        String body = "Hello " + user.getFullName() + ",\n\n"
                + (passwordReset
                    ? "An administrator reset your King's Farm password. Use the link below to choose a new one."
                    : "An account has been created for you on King's Farm. Use the link below to verify your email address and choose your password.")
                + "\n\n" + link + "\n\n"
                + "This link works once and expires in " + validHours + " hours. If you were not expecting this email, you can ignore it.\n";

        if (host == null || host.isBlank() || mailSender.getIfAvailable() == null) {
            log.warn("SMTP not configured (MAIL_HOST empty) - setup link for {}: {}", user.getUsername(), link);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(user.getUsername());
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.getObject().send(msg);
            return true;
        } catch (Exception ex) {
            log.error("Could not send email to {} ({}). Setup link: {}", user.getUsername(), ex.getMessage(), link);
            return false;
        }
    }
}
