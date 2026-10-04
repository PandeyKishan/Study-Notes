package com.cloudnative.notificationservice.listener;

import com.cloudnative.notificationservice.dto.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    @KafkaListener(
            topics = "${app.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderEvent(
            @Payload OrderEvent order,
            @Header(KafkaHeaders.RECEIVED_KEY) String customerKey,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[NotificationService] 📩 Received Event from Kafka:");
        log.info("  ├─ Partition: {}", partition);
        log.info("  ├─ Offset:    {}", offset);
        log.info("  ├─ Key:       {}", customerKey);
        log.info("  └─ Order ID:  {}", order.orderId());

        // Simulate business action: sending notification email/SMS
        dispatchCustomerNotification(order);
    }

    private void dispatchCustomerNotification(OrderEvent order) {
        log.info("[NotificationService] 🚀 [EMAIL SENT] Confirmation sent to Customer '{}' for item '{}' (Total: ${})",
                order.customerId(), order.item(), order.amount());
    }
}
