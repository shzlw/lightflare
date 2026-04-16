package com.lightflare.server.tools.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final EmailProperties emailProperties;

    /**
     * Send an email with the given parameters
     * 
     * @param to recipient email address
     * @param subject email subject
     * @param text email body text
     * @return true if email was sent successfully, false otherwise
     */
    public boolean send(String to, String subject, String text) {
        return send(to, subject, text, null);
    }

    /**
     * Send an email with the given parameters
     * 
     * @param to recipient email address
     * @param subject email subject
     * @param text email body text
     * @param from sender email address (optional, uses configured default if not provided)
     * @return true if email was sent successfully, false otherwise
     */
    public boolean send(String to, String subject, String text, String from) {
        try {
            // Validate required fields
            if (!StringUtils.hasText(to)) {
                log.warn("Cannot send email: recipient address is empty");
                return false;
            }

            if (!StringUtils.hasText(subject)) {
                log.warn("Cannot send email: subject is empty");
                return false;
            }

            if (!StringUtils.hasText(text)) {
                log.warn("Cannot send email: body text is empty");
                return false;
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);

            // Use provided from address, fall back to configured default
            String fromAddress = StringUtils.hasText(from) ? from : emailProperties.getFrom();
            if (StringUtils.hasText(fromAddress)) {
                message.setFrom(fromAddress);
            }

            javaMailSender.send(message);
            log.info("Email sent successfully to: {}", to);
            return true;

        } catch (Exception e) {
            log.error("Failed to send email to: {}", to, e);
            return false;
        }
    }
}
