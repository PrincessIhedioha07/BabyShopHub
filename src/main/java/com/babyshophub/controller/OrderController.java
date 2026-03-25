package com.babyshophub.controller;

import com.babyshophub.model.Order;
import com.babyshophub.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Place and manage orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "List my orders with optional status filter")
    @GetMapping
    public ResponseEntity<Page<Order>> myOrders(
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderService.getMyOrders(status, page, size));
    }

    @Operation(summary = "Get order detail")
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @Operation(summary = "Cancel an order (CONFIRMED or PROCESSING only)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }

    @Operation(summary = "Get order tracking info")
    @GetMapping("/{id}/tracking")
    public ResponseEntity<Map<String, Object>> tracking(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        return ResponseEntity.ok(Map.of(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "estimatedDelivery", order.getEstimatedDeliveryDate() != null
                        ? order.getEstimatedDeliveryDate().toString() : "TBD"
        ));
    }

    @Operation(summary = "Place a new order")
    @PostMapping
    public ResponseEntity<Map<String, Object>> placeOrder(@RequestBody Map<String, Object> body) {
        Order order = orderService.placeOrder(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "orderId", order.getId(),
                "message", "Order placed successfully",
                "status", order.getStatus().name()
        ));
    }
}
