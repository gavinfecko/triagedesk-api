package dev.gavinfecko.triagedesk.notification.infra;

import dev.gavinfecko.triagedesk.notification.application.Email;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends one email. A failure is logged and counted, never thrown: the change that caused the email has already
 * committed and must not look failed to the caller. Without a mail server configured, nothing is sent.
 */
@Component
public class MailGateway {

    private static final Logger log = LoggerFactory.getLogger(MailGateway.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final MeterRegistry meters;
    private final String from;

    public MailGateway(
            ObjectProvider<JavaMailSender> sender,
            MeterRegistry meters,
            @Value("${triagedesk.mail.from}") String from) {
        this.sender = sender;
        this.meters = meters;
        this.from = from;
    }

    public boolean send(Email email) {
        JavaMailSender mail = sender.getIfAvailable();
        if (mail == null) {
            meters.counter("notifications.email", "result", "skipped").increment();
            return false;
        }
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            mail.send(message);
            meters.counter("notifications.email", "result", "sent").increment();
            return true;
        } catch (Exception e) { // any failure: SMTP down, bad address, encoding
            meters.counter("notifications.email", "result", "failed").increment();
            log.warn("Email '{}' to {} not sent: {}", email.subject(), email.to(), e.toString());
            return false;
        }
    }
}
