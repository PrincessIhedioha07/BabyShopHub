package com.babyshophub.controller;

import com.babyshophub.model.Order;
import com.babyshophub.model.User;
import com.babyshophub.repository.OrderRepository;
import com.babyshophub.repository.ProductRepository;
import com.babyshophub.repository.UserRepository;
import com.babyshophub.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin panel — users, orders, products, analytics")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderService orderService;

    // ── Dashboard ───────────────────────────────────────────
    @Operation(summary = "Admin dashboard stats")
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        long totalUsers = userRepository.count();
        long totalOrders = orderRepository.count();
        long totalProducts = productRepository.count();
        return ResponseEntity.ok(Map.of(
                "totalUsers", totalUsers,
                "totalOrders", totalOrders,
                "totalProducts", totalProducts,
                "totalRevenue", "—" // would compute from orders in production
        ));
    }

    // ── Users ───────────────────────────────────────────────
    @Operation(summary = "List all users")
    @GetMapping("/users")
    public ResponseEntity<Page<User>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userRepository.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    @Operation(summary = "Suspend a user")
    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<User> suspendUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setSuspended(true);
        return ResponseEntity.ok(userRepository.save(user));
    }

    @Operation(summary = "Unsuspend a user")
    @PutMapping("/users/{id}/unsuspend")
    public ResponseEntity<User> unsuspendUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setSuspended(false);
        return ResponseEntity.ok(userRepository.save(user));
    }

    @Operation(summary = "Delete a user")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Orders ──────────────────────────────────────────────
    @Operation(summary = "List all orders")
    @GetMapping("/orders")
    public ResponseEntity<Page<Order>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(orderRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(page, size)));
    }

    @Operation(summary = "Update order status — triggers FCM push")
    @PutMapping("/orders/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(body.get("status").toUpperCase());
        return ResponseEntity.ok(orderService.updateStatus(id, newStatus));
    }

    // ── Products ─────────────────────────────────────────────
    @Operation(summary = "Toggle product featured flag")
    @PutMapping("/products/{id}/feature")
    public ResponseEntity<Map<String, Object>> featureProduct(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        return productRepository.findById(id).map(p -> {
            p.setFeatured(body.getOrDefault("featured", true));
            productRepository.save(p);
            return ResponseEntity.ok(Map.<String, Object>of("id", id, "featured", p.isFeatured()));
        }).orElse(ResponseEntity.notFound().build());
    }
}
