package com.example.notification.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * A separate bean is required for {@code @Async} to actually take effect — a self-invoked
 * {@code @Async} method called from within the same class bypasses Spring's AOP proxy and runs
 * synchronously, silently defeating the whole point (see AsyncConfig's javadoc for why this needs
 * to run off the Kafka listener thread at all).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailDispatcher {

    private final JavaMailSender mailSender;

    /**
     * {@code @Async void}: the caller can't observe success/failure through the return value the
     * way it could with a synchronous call, so failures are logged here rather than left to
     * Spring's default uncaught-async-exception handler (which just stack-traces to the console).
     */
    @Async("emailTaskExecutor")
    public void send(SimpleMailMessage message) {
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("Failed to send email to {}: {}", message.getTo(), e.toString());
        }
    }
}
