package com.example.common.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Every topic in {@link com.example.common.events.Topics} is otherwise auto-created with the
 * broker's default (1 partition, no {@code NewTopic} bean existed anywhere in this codebase before
 * {@link KafkaTopicAutoConfiguration}). 1 partition caps every consumer group on that topic at
 * exactly one active consumer no matter how much {@code @KafkaListener(concurrency = ...)} or
 * Kafka Streams' {@code num.stream.threads} is turned up — see notification-service's
 * NotificationListener and reporting-service's application.yaml for services that size their own
 * concurrency to match {@link #partitions()}.
 */
@ConfigurationProperties("kafka.topics")
public record KafkaTopicProperties(
        @DefaultValue("3") int partitions,
        @DefaultValue("1") short replicationFactor) {
}
