package com.kingsfarm.kingsfarmbackend.user;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

/**
 * Sends branded HTML emails (with a plain-text alternative) over SMTP. If
 * MAIL_HOST is blank (local dev / SMTP not set up) nothing is sent and the
 * link is written to the server log instead. Never throws: a mail failure
 * must not break the action that triggered it.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String GREEN = "#0c2116";
    private static final String AMBER = "#f59e0b";

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

    /** Absolute link to a page inside the app, e.g. {@code appLink("/admin/opening-stock")}. */
    public String appLink(String path) {
        return frontendBaseUrl + (path.startsWith("/") ? path : "/" + path);
    }

    /** @return true if the email was handed to the SMTP server. */
    public boolean sendAccountSetup(User user, String rawToken, boolean passwordReset, int validHours) {
        String subject = passwordReset ? "Reset your King Farms password" : "Verify your email for King Farms";
        String lead = passwordReset
                ? "An administrator reset your King Farms password. Use the button below to choose a new one."
                : "An account has been created for you on King Farms. Use the button below to verify your email address and choose your password.";
        return send(user.getUsername(), subject,
                passwordReset ? "Reset your password" : "Verify your email",
                "Hello " + user.getFullName() + ",",
                List.of(lead),
                passwordReset ? "Choose a new password" : "Verify & set password",
                linkFor(rawToken),
                "This link works once and expires in " + validHours + " hours. If you were not expecting this email, you can ignore it.");
    }

    /**
     * Generic notification email.
     * @param greeting   first line, e.g. "Hello Amaka,"
     * @param paragraphs body paragraphs (plain text; HTML-escaped here)
     * @param buttonLabel text of the call-to-action button (null for none)
     * @param link       absolute URL for the button (null for none)
     * @param footnote   small print under the button (nullable)
     */
    public boolean send(String to, String subject, String heading, String greeting, List<String> paragraphs,
                        String buttonLabel, String link, String footnote) {
        if (host == null || host.isBlank() || mailSender.getIfAvailable() == null) {
            log.warn("SMTP not configured (MAIL_HOST empty) - email to {} ['{}']{}", to, subject, link != null ? " link: " + link : "");
            return false;
        }
        try {
            MimeMessage message = mailSender.getObject().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, "King Farms");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText(greeting, paragraphs, buttonLabel, link, footnote),
                    html(heading, greeting, paragraphs, buttonLabel, link, footnote));
            mailSender.getObject().send(message);
            return true;
        } catch (Exception ex) {
            log.error("Could not send email to {} ({}){}", to, ex.getMessage(), link != null ? ". Link: " + link : "");
            return false;
        }
    }

    private String plainText(String greeting, List<String> paragraphs, String buttonLabel, String link, String footnote) {
        StringBuilder sb = new StringBuilder(greeting).append("\n\n");
        paragraphs.forEach(p -> sb.append(p).append("\n\n"));
        if (link != null) sb.append(buttonLabel).append(": ").append(link).append("\n\n");
        if (footnote != null) sb.append(footnote).append("\n");
        return sb.toString();
    }

    private String html(String heading, String greeting, List<String> paragraphs, String buttonLabel, String link, String footnote) {
        StringBuilder body = new StringBuilder();
        for (String p : paragraphs) {
            body.append("<p style=\"margin:0 0 16px;font-size:15px;line-height:1.6;color:#374151;\">")
                .append(HtmlUtils.htmlEscape(p)).append("</p>");
        }
        String button = link == null ? "" :
                "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"margin:24px 0;\"><tr><td style=\"background:" + AMBER + ";border-radius:8px;\">"
                + "<a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"display:inline-block;padding:13px 28px;font-size:15px;font-weight:bold;color:" + GREEN + ";text-decoration:none;\">"
                + HtmlUtils.htmlEscape(buttonLabel) + "</a></td></tr></table>"
                + "<p style=\"margin:0 0 16px;font-size:12px;line-height:1.5;color:#6b7280;\">Button not working? Copy this link into your browser:<br>"
                + "<a href=\"" + HtmlUtils.htmlEscape(link) + "\" style=\"color:#047857;word-break:break-all;\">" + HtmlUtils.htmlEscape(link) + "</a></p>";
        String foot = footnote == null ? "" : "<p style=\"margin:0;font-size:12px;line-height:1.5;color:#6b7280;\">" + HtmlUtils.htmlEscape(footnote) + "</p>";

        return "<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#f3f4f6;padding:24px 12px;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"560\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:560px;width:100%;background:#ffffff;border-radius:12px;overflow:hidden;\">"
                + "<tr><td align=\"center\" style=\"background:" + GREEN + ";padding:24px;\">"
                + "<img src=\"" + frontendBaseUrl + "/email-logo.png\" alt=\"Kingfarms\" width=\"140\" style=\"display:block;border:0;height:auto;max-width:140px;\">"
                + "<div style=\"margin-top:10px;font-size:12px;letter-spacing:1px;color:#6ee7b7;\">POULTRY &amp; FISH FARM MANAGEMENT SYSTEM</div></td></tr>"
                + "<tr><td style=\"padding:32px 32px 24px;\">"
                + "<h1 style=\"margin:0 0 20px;font-size:22px;color:" + GREEN + ";\">" + HtmlUtils.htmlEscape(heading) + "</h1>"
                + "<p style=\"margin:0 0 16px;font-size:15px;color:#374151;\">" + HtmlUtils.htmlEscape(greeting) + "</p>"
                + body + button + foot
                + "</td></tr>"
                + "<tr><td style=\"background:#f9fafb;padding:16px 32px;font-size:11px;color:#9ca3af;text-align:center;\">"
                + "Kingfarms Limited &middot; This is an automated message, please do not reply.</td></tr>"
                + "</table></td></tr></table></body></html>";
    }
}
