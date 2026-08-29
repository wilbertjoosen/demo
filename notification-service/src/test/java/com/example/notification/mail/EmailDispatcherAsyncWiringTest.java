package com.example.notification.mail;

import com.example.notification.config.AsyncConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Proves {@code @Async("emailTaskExecutor")} actually routes through AsyncConfig's bounded
 * executor and off the calling thread — a bean-name mismatch between the qualifier here and the
 * {@code @Bean(name = ...)} there is a classic silent failure (Spring falls back to a default
 * {@code SimpleAsyncTaskExecutor} rather than erroring), so "it compiles" doesn't prove the wiring
 * is actually correct. A real (small) Spring context, not just a Mockito unit test, because the
 * whole point is exercising the {@code @EnableAsync} proxy machinery itself.
 */
class EmailDispatcherAsyncWiringTest {

    @Test
    void send_runsOnADedicatedEmailThread_notTheCallingThread() throws InterruptedException {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> observedThreadName = new AtomicReference<>();
        doAnswer(invocation -> {
            observedThreadName.set(Thread.currentThread().getName());
            latch.countDown();
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JavaMailSender.class, () -> mailSender);
            context.register(AsyncConfig.class, EmailDispatcher.class);
            context.refresh();

            String callingThreadName = Thread.currentThread().getName();
            context.getBean(EmailDispatcher.class).send(new SimpleMailMessage());

            assertThat(latch.await(2, TimeUnit.SECONDS)).as("send() should complete asynchronously").isTrue();
            assertThat(observedThreadName.get()).isNotEqualTo(callingThreadName);
            assertThat(observedThreadName.get()).startsWith("email-");
        }
    }
}
