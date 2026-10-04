package com.cloudnative.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderRequest(
    @NotBlank(message = "Customer ID is required")
    String customerId,
    
    @NotBlank(message = "Product ID is required")
    String productId,
    
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    Integer quantity,
    
    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price cannot be negative")
    BigDecimal price
) {}
