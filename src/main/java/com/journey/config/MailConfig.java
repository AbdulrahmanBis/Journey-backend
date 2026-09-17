package com.journey.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * The SMTP connection for notification email, from {@code app.mail.*} (in Docker: the {@code SMTP_*} values
 * in {@code .env}).
 *
 * <p>Our own properties rather than {@code spring.mail.*}: Docker Compose cannot leave a variable out when it
 * is empty, and an empty {@code spring.mail.host} would still create a sender that fails on every message.
 * Here no host means no sender, and {@code MailNotificationSender} says so clearly at startup.
 *
 * <p>Sending still needs {@code app.notifications.mail.enabled=true}; configuring a server alone sends nothing.
 */
@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnExpression("'${app.mail.host:}'.trim().length() > 0")
    public JavaMailSender journeyMailSender(
            @Value("${app.mail.host}") String host,
            @Value("${app.mail.port:587}") int port,
            @Value("${app.mail.username:}") String username,
            @Value("${app.mail.password:}") String password,
            @Value("${app.mail.starttls:true}") boolean starttls,
            @Value("${app.mail.timeout-ms:15000}") int timeoutMs) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host.trim());
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        boolean auth = !username.isBlank();
        if (auth) {
            sender.setUsername(username.trim());
            sender.setPassword(password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.starttls.required", String.valueOf(starttls));
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.timeout", String.valueOf(timeoutMs));
        props.put("mail.smtp.writetimeout", String.valueOf(timeoutMs));
        return sender;
    }
}
