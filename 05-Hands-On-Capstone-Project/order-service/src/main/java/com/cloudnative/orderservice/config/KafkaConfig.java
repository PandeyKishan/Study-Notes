package com.cloudnative.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic}")
    private String topicName;

    @Value("${app.kafka.partitions:3}")
    private int partitions;

    @Value("${app.kafka.replication-factor:1}")
    private short replicationFactor;

    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
}
