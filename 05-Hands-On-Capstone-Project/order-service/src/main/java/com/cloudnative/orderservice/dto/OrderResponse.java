package com.cloudnative.orderservice.dto;

public record OrderResponse(
    String message,
    OrderEvent order,
    KafkaRecordMetadata kafkaMetadata
) {
    public record KafkaRecordMetadata(
        String topic,
        int partition,
        long offset
    ) {}
}
