package com.cloudnative.orderservice.service;

import com.cloudnative.orderservice.event.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;

@Service
public class OrderProducerService {

    private static final Logger log = LoggerFactory.getLogger(OrderProducerService.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topicName;

    public OrderProducerService(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topic}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    public void sendOrderEvent(OrderEvent event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topicName, event.orderId().toString(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully sent order event for ID: {} to partition: {} with offset: {}",
                        event.orderId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send order event for ID: {}", event.orderId(), ex);
            }
        });
    }
}
