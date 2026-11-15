package com.cloudnative.notificationservice.consumer;

import com.cloudnative.notificationservice.event.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeOrderEvent(@Payload OrderEvent event, Acknowledgment acknowledgment) {
        try {
            log.info("Received Order Event: {}", event);
            
            // Process the notification...
            log.info("Sending notification for Order ID: {} to Customer ID: {}", 
                event.orderId(), event.customerId());
                
            // Acknowledge the message only after successful processing
            acknowledgment.acknowledge();
            log.debug("Successfully acknowledged message for Order ID: {}", event.orderId());
            
        } catch (Exception e) {
            log.error("Error processing Order Event for Order ID: {}", event.orderId(), e);
            // DO NOT acknowledge on error so it can be retried or sent to DLQ
        }
    }
}
