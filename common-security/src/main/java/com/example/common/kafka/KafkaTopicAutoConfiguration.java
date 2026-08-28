package com.example.common.kafka;

import com.example.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Declares every topic in {@link Topics} with an explicit partition count, shared across every
 * service instead of duplicated per-consumer. Registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} —
 * applies automatically to any service with Kafka on its classpath, same mechanism as
 * MongoPoolAutoConfiguration. Spring's {@code KafkaAdmin} only creates topics that don't already
 * exist — see {@link KafkaTopicProperties}'s javadoc for the "whichever service boots first wins
 * the initial partition count" caveat that follows from that.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaAdmin.class)
@EnableConfigurationProperties(KafkaTopicProperties.class)
public class KafkaTopicAutoConfiguration {

    private final KafkaTopicProperties properties;

    public KafkaTopicAutoConfiguration(KafkaTopicProperties properties) {
        this.properties = properties;
    }

    private NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(properties.partitions()).replicas(properties.replicationFactor()).build();
    }

    @Bean
    NewTopic userEventsTopic() {
        return topic(Topics.USER_EVENTS);
    }

    @Bean
    NewTopic productEventsTopic() {
        return topic(Topics.PRODUCT_EVENTS);
    }

    @Bean
    NewTopic orderEventsTopic() {
        return topic(Topics.ORDER_EVENTS);
    }

    @Bean
    NewTopic paymentEventsTopic() {
        return topic(Topics.PAYMENT_EVENTS);
    }

    @Bean
    NewTopic shippingEventsTopic() {
        return topic(Topics.SHIPPING_EVENTS);
    }

    @Bean
    NewTopic deliveryEventsTopic() {
        return topic(Topics.DELIVERY_EVENTS);
    }

    @Bean
    NewTopic inventoryEventsTopic() {
        return topic(Topics.INVENTORY_EVENTS);
    }
}
