package com.babyshophub.controller;

import com.babyshophub.model.Cart;
import com.babyshophub.model.CartItem;
import com.babyshophub.model.Product;
import com.babyshophub.model.User;
import com.babyshophub.repository.CartItemRepository;
import com.babyshophub.repository.CartRepository;
import com.babyshophub.repository.ProductRepository;
import com.babyshophub.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart", description = "Shopping cart — add, update, remove items and apply promos")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CartController {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    private User getAuthenticatedUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Cart getOrCreateCart() {
        User user = getAuthenticatedUser();
        return cartRepository.findByUserId(user.getId()).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(user);
            return cartRepository.save(cart);
        });
    }

    @Operation(summary = "Get cart contents")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getCart() {
        Cart cart = getOrCreateCart();
        
        List<Map<String, Object>> itemsList = cart.getItems().stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            Product p = item.getProduct();
            map.put("id", item.getId());
            map.put("productId", p.getId());
            map.put("name", p.getName());
            map.put("price", p.getPrice());
            map.put("qty", item.getQuantity());
            if (p.getImages() != null && !p.getImages().isEmpty()) {
                map.put("image", p.getImages().get(0).get("url"));
            } else {
                map.put("image", null);
            }
            return map;
        }).collect(Collectors.toList());

        double subtotal = cart.getItems().stream()
                .mapToDouble(i -> i.getProduct().getPrice().doubleValue() * i.getQuantity())
                .sum();
                
        int itemCount = cart.getItems().stream().mapToInt(CartItem::getQuantity).sum();

        return ResponseEntity.ok(Map.of(
            "items", itemsList,
            "subtotal", subtotal,
            "itemCount", itemCount
        ));
    }

    @Operation(summary = "Add item to cart")
    @PostMapping("/items")
    public ResponseEntity<Map<String, Object>> addItem(@RequestBody Map<String, Object> body) {
        Cart cart = getOrCreateCart();
        Long productId = ((Number) body.get("productId")).longValue();
        int quantity = ((Number) body.get("quantity")).intValue();

        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("Product not found"));

        Optional<CartItem> existingItem = cart.getItems().stream()
            .filter(i -> i.getProduct().getId().equals(productId))
            .findFirst();

        CartItem cartItem;
        if (existingItem.isPresent()) {
            cartItem = existingItem.get();
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
        } else {
            cartItem = new CartItem();
            cartItem.setCart(cart);
            cartItem.setProduct(product);
            cartItem.setQuantity(quantity);
            cartItem.setUnitPrice(product.getPrice());
            cart.getItems().add(cartItem);
        }
        
        cartItemRepository.save(cartItem);

        return ResponseEntity.ok(Map.of(
            "message", "Item added to cart",
            "cartItemId", "CI-" + cartItem.getId()
        ));
    }

    @Operation(summary = "Update cart item quantity")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<Map<String, String>> updateItem(
            @PathVariable Long itemId,
            @RequestBody Map<String, Integer> body) {
        Cart cart = getOrCreateCart();
        CartItem item = cartItemRepository.findById(itemId).orElseThrow();
        if (!item.getCart().getId().equals(cart.getId())) {
            return ResponseEntity.status(403).build();
        }
        item.setQuantity(body.get("quantity"));
        cartItemRepository.save(item);
        return ResponseEntity.ok(Map.of("message", "Cart updated"));
    }

    @Operation(summary = "Remove item from cart")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> removeItem(@PathVariable Long itemId) {
        Cart cart = getOrCreateCart();
        CartItem item = cartItemRepository.findById(itemId).orElseThrow();
        if (item.getCart().getId().equals(cart.getId())) {
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Apply promo code")
    @PostMapping("/promo")
    public ResponseEntity<Map<String, Object>> applyPromo(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if ("BABY10".equalsIgnoreCase(code) || "FREESHIP".equalsIgnoreCase(code) || "WELCOME20".equalsIgnoreCase(code)) {
            return ResponseEntity.ok(Map.of(
                "code", code.toUpperCase(),
                "discount", code.equalsIgnoreCase("BABY10") ? 10 : code.equalsIgnoreCase("FREESHIP") ? 9.99 : 20,
                "type", code.equalsIgnoreCase("FREESHIP") ? "FIXED" : "PERCENT",
                "valid", true
            ));
        }
        return ResponseEntity.badRequest().body(Map.of("valid", false, "message", "Promo code not found or expired"));
    }

    @Operation(summary = "Clear cart")
    @DeleteMapping
    public ResponseEntity<Void> clearCart() {
        Cart cart = getOrCreateCart();
        cartItemRepository.deleteAll(cart.getItems());
        cart.getItems().clear();
        return ResponseEntity.noContent().build();
    }
}
