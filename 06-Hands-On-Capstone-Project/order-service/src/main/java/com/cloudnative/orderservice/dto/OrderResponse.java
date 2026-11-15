package com.cloudnative.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
    UUID orderId,
    String customerId,
    String productId,
    Integer quantity,
    BigDecimal price,
    String status,
    Instant createdAt
) {}
