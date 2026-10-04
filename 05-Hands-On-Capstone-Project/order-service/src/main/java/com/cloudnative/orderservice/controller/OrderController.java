package com.cloudnative.orderservice.controller;

import com.cloudnative.orderservice.dto.OrderRequest;
import com.cloudnative.orderservice.dto.OrderResponse;
import com.cloudnative.orderservice.event.OrderEvent;
import com.cloudnative.orderservice.service.OrderProducerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    private final OrderProducerService producerService;

    public OrderController(OrderProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("Received request to create order for customer: {}", request.customerId());
        
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();
        String status = "CREATED";
        
        OrderEvent event = new OrderEvent(
                UUID.randomUUID(),
                orderId,
                request.customerId(),
                request.productId(),
                request.quantity(),
                request.price(),
                status,
                now
        );
        
        producerService.sendOrderEvent(event);
        
        OrderResponse response = new OrderResponse(
                orderId,
                request.customerId(),
                request.productId(),
                request.quantity(),
                request.price(),
                status,
                now
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
