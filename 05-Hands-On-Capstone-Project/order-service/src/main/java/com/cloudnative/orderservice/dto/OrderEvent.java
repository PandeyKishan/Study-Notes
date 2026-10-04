package com.cloudnative.orderservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEvent(
    String orderId,
    String customerId,
    String item,
    BigDecimal amount,
    String status,
    Instant timestamp
) {}
