package com.cloudnative.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record OrderRequest(
    @NotBlank(message = "Customer ID must not be blank")
    String customerId,

    @NotBlank(message = "Item name must not be blank")
    String item,

    @NotNull(message = "Amount must not be null")
    @Positive(message = "Amount must be strictly positive")
    BigDecimal amount
) {}
