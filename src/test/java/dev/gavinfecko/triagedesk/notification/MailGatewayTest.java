package dev.gavinfecko.triagedesk.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.gavinfecko.triagedesk.notification.application.Email;
import dev.gavinfecko.triagedesk.notification.infra.MailGateway;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

/** A send failure is logged and counted, never thrown back at the request that caused it. */
class MailGatewayTest {

    static final Email EMAIL = new Email("rosa@clinic.test", "[HD-001000] Hello", "text", "<p>html</p>");

    @Test
    void aFailedSendIsCountedAndSwallowed() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("connection refused")).when(sender).send(any(MimeMessage.class));
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        MailGateway gateway = new MailGateway(provider(sender), meters, "TriageDesk <helpdesk@triagedesk.local>");

        assertThat(gateway.send(EMAIL)).isFalse();
        assertThat(meters.counter("notifications.email", "result", "failed").count())
                .isEqualTo(1);
        assertThat(meters.counter("notifications.email", "result", "sent").count())
                .isZero();
    }

    @Test
    void withoutAMailServerNothingIsSent() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        MailGateway gateway = new MailGateway(
                new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class),
                meters,
                "helpdesk@triagedesk.local");
        assertThat(gateway.send(EMAIL)).isFalse();
        assertThat(meters.counter("notifications.email", "result", "skipped").count())
                .isEqualTo(1);
    }

    static org.springframework.beans.factory.ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("mailSender", sender);
        return beans.getBeanProvider(JavaMailSender.class);
    }
}
