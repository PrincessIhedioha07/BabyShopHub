package com.babyshophub.service;

import com.babyshophub.model.Order;
import com.babyshophub.model.User;
import com.babyshophub.repository.OrderRepository;
import com.babyshophub.repository.UserRepository;
import com.babyshophub.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final com.babyshophub.repository.ProductRepository productRepository;
    private final com.babyshophub.repository.AddressRepository addressRepository;

    public Page<Order> getMyOrders(String status, int page, int size) {
        User user = getCurrentUser();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (status != null && !status.equalsIgnoreCase("all")) {
            Order.OrderStatus os = Order.OrderStatus.valueOf(status.toUpperCase());
            return orderRepository.findByUserIdAndStatus(user.getId(), os, pageable);
        }
        return orderRepository.findByUserId(user.getId(), pageable);
    }

    public Order getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        User currentUser = getCurrentUser();
        if (!order.getUser().getId().equals(currentUser.getId())
                && !currentUser.getRole().name().equals("ADMIN")) {
            throw new SecurityException("Access denied");
        }
        return order;
    }

    @Transactional
    public Order cancelOrder(Long id) {
        Order order = getOrderById(id);
        if (order.getStatus() != Order.OrderStatus.CONFIRMED
                && order.getStatus() != Order.OrderStatus.PROCESSING) {
            throw new IllegalStateException("Order cannot be cancelled in status: " + order.getStatus());
        }
        order.setStatus(Order.OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    @Transactional
    public Order updateStatus(Long id, Order.OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    @Transactional
    public Order placeOrder(java.util.Map<String, Object> body) {
        User user = getCurrentUser();

        com.babyshophub.model.Cart cart = cartRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        @SuppressWarnings("unchecked")
        java.util.Map<String, String> addressData = (java.util.Map<String, String>) body.get("shippingAddress");
        com.babyshophub.model.Address address = com.babyshophub.model.Address.builder()
                .user(user)
                .label("Delivery")
                .recipientName(addressData != null && addressData.containsKey("name") ? addressData.get("name") : user.getFirstName() + " " + user.getLastName())
                .addressLine1(addressData != null && addressData.containsKey("street") ? addressData.get("street") : "N/A")
                .city(addressData != null && addressData.containsKey("city") ? addressData.get("city") : "N/A")
                .postalCode(addressData != null && addressData.containsKey("zipCode") ? addressData.get("zipCode") : "00000")
                .country("US")
                .isDefault(false)
                .build();
        address = addressRepository.save(address);

        String deliveryTypeStr = (String) body.getOrDefault("deliveryType", "STANDARD");
        Order.DeliveryType deliveryType = Order.DeliveryType.valueOf(deliveryTypeStr.toUpperCase());

        java.math.BigDecimal subtotal = java.math.BigDecimal.ZERO;
        for (com.babyshophub.model.CartItem ci : cart.getItems()) {
            java.math.BigDecimal qty = new java.math.BigDecimal(ci.getQuantity());
            subtotal = subtotal.add(ci.getUnitPrice().multiply(qty));
            
            if (ci.getProduct() != null) {
                com.babyshophub.model.Product p = ci.getProduct();
                p.setStockQty(Math.max(0, p.getStockQty() - ci.getQuantity()));
                productRepository.save(p);
            }
        }

        java.math.BigDecimal deliveryFee = deliveryType == Order.DeliveryType.EXPRESS ? new java.math.BigDecimal("9.99") : 
                               (deliveryTypeStr.equalsIgnoreCase("SAME_DAY") ? new java.math.BigDecimal("19.99") : java.math.BigDecimal.ZERO);
        java.math.BigDecimal discount = java.math.BigDecimal.ZERO;
        java.math.BigDecimal total = subtotal.add(deliveryFee).subtract(discount);

        Order order = Order.builder()
                .user(user)
                .address(address)
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .discount(discount)
                .total(total)
                .deliveryType(deliveryType)
                .status(Order.OrderStatus.CONFIRMED)
                .estimatedDeliveryDate(java.time.LocalDate.now().plusDays(deliveryType == Order.DeliveryType.STANDARD ? 3 : 1))
                .build();

        for (com.babyshophub.model.CartItem ci : cart.getItems()) {
            com.babyshophub.model.OrderItem oi = com.babyshophub.model.OrderItem.builder()
                    .order(order)
                    .product(ci.getProduct())
                    .variantId(ci.getVariantId())
                    .productName(ci.getProduct() != null ? ci.getProduct().getName() : "Unknown Product")
                    .quantity(ci.getQuantity())
                    .unitPrice(ci.getUnitPrice())
                    .build();
            order.getItems().add(oi);
        }

        order = orderRepository.save(order);

        cart.getItems().clear();
        cartRepository.save(cart);

        return order;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
