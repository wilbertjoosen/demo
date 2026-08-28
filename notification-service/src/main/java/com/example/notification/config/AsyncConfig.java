package com.example.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated bounded executor for email sends — see EmailDispatcher for why this needs to exist at
 * all: mailSender.send() is blocking SMTP I/O, and NotificationListener's single @KafkaListener
 * method runs on the same consumer thread that pulls every event off every topic this service
 * subscribes to (user/product/order/payment/shipping/delivery/inventory-events), so a slow mail
 * server would otherwise stall event processing entirely, not just delay emails.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor(
            @Value("${notifications.mail.executor.core-pool-size:4}") int corePoolSize,
            @Value("${notifications.mail.executor.max-pool-size:8}") int maxPoolSize,
            @Value("${notifications.mail.executor.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("email-");
        // CallerRunsPolicy over discarding or throwing: under sustained overload this pushes back
        // onto the Kafka listener thread (temporarily serializing sends) instead of silently
        // losing a customer-facing email or crashing the listener with a rejection exception.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
