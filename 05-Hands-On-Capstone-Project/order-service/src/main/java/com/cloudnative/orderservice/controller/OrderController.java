package com.cloudnative.orderservice.controller;

import com.cloudnative.orderservice.dto.OrderEvent;
import com.cloudnative.orderservice.dto.OrderRequest;
import com.cloudnative.orderservice.dto.OrderResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private final String topicName;

    public OrderController(KafkaTemplate<String, OrderEvent> kafkaTemplate,
                           @Value("${app.kafka.topic}") String topicName) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicName = topicName;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<OrderResponse>> createOrder(@Valid @RequestBody OrderRequest request) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        OrderEvent event = new OrderEvent(
                orderId,
                request.customerId(),
                request.item(),
                request.amount(),
                "CREATED",
                Instant.now()
        );

        log.info("[OrderService] Publishing OrderPlaced event: ID={} for Customer={}", orderId, request.customerId());

        // Send to Kafka with customerId as the KEY to ensure partition pinning
        return kafkaTemplate.send(topicName, request.customerId(), event)
                .thenApply((SendResult<String, OrderEvent> result) -> {
                    var metadata = result.getRecordMetadata();
                    log.info("[OrderService] Successfully published order {} to partition {} at offset {}",
                            orderId, metadata.partition(), metadata.offset());

                    OrderResponse.KafkaRecordMetadata kafkaMeta = new OrderResponse.KafkaRecordMetadata(
                            metadata.topic(),
                            metadata.partition(),
                            metadata.offset()
                    );

                    OrderResponse response = new OrderResponse(
                            "Order successfully created and published to Kafka",
                            event,
                            kafkaMeta
                    );

                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .exceptionally(ex -> {
                    log.error("[OrderService] Failed to publish order event to Kafka: {}", ex.getMessage(), ex);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(new OrderResponse("Failed to publish order to Kafka broker", event, null));
                });
    }
}
