package com.cloudnative.notificationservice.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderEvent(
    UUID eventId,
    UUID orderId,
    String customerId,
    String productId,
    Integer quantity,
    BigDecimal price,
    String status,
    Instant timestamp
) {}
