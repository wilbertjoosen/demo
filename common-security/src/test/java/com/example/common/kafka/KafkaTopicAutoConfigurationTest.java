package com.example.common.kafka;

import com.example.common.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaTopicAutoConfiguration.class));

    @Test
    void defaultProperties_declaresEveryTopicWithThreePartitions() {
        contextRunner.run(context -> {
            Map<String, NewTopic> topics = context.getBeansOfType(NewTopic.class);
            assertThat(topics.values()).extracting(NewTopic::name).containsExactlyInAnyOrder(
                    Topics.USER_EVENTS, Topics.PRODUCT_EVENTS, Topics.ORDER_EVENTS, Topics.PAYMENT_EVENTS,
                    Topics.SHIPPING_EVENTS, Topics.DELIVERY_EVENTS, Topics.INVENTORY_EVENTS);
            assertThat(topics.values()).allSatisfy(topic -> {
                assertThat(topic.numPartitions()).isEqualTo(3);
                assertThat(topic.replicationFactor()).isEqualTo((short) 1);
            });
        });
    }

    @Test
    void customPartitionCount_appliesToEveryTopic() {
        contextRunner.withPropertyValues("kafka.topics.partitions=6").run(context -> {
            NewTopic orderEvents = context.getBean("orderEventsTopic", NewTopic.class);
            assertThat(orderEvents.numPartitions()).isEqualTo(6);
        });
    }
}
