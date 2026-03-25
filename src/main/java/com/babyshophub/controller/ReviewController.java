package com.babyshophub.controller;

import com.babyshophub.model.Product;
import com.babyshophub.model.Review;
import com.babyshophub.model.User;
import com.babyshophub.repository.ProductRepository;
import com.babyshophub.repository.ReviewRepository;
import com.babyshophub.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Product reviews — submit and manage")
@SecurityRequirement(name = "bearerAuth")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // ── Public: list reviews for a product ─────────────────────────────────

    @Operation(summary = "List visible reviews for a product")
    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<Review>> listByProduct(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                reviewRepository.findByProductIdAndHiddenFalse(
                        productId, PageRequest.of(page, size, Sort.by("createdAt").descending())));
    }

    // ── Customer: submit a review ───────────────────────────────────────────

    @Operation(summary = "Submit a review (CUSTOMER only)")
    @PostMapping("/product/{productId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Review> create(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails principal) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow();

        short rating = Short.parseShort(body.getOrDefault("rating", "5").toString());
        if (rating < 1 || rating > 5) {
            return ResponseEntity.badRequest().build();
        }

        Review review = Review.builder()
                .user(user)
                .product(product)
                .rating(rating)
                .title((String) body.get("title"))
                .text((String) body.get("text"))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(reviewRepository.save(review));
    }

    // ── Delete: CUSTOMER (own), SELLER (on own product), ADMIN (any) ────────

    @Operation(summary = "Delete a review — CUSTOMER (own), SELLER (on their product), or ADMIN")
    @DeleteMapping("/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(
            @PathVariable Long reviewId,
            @AuthenticationPrincipal UserDetails principal) {

        User caller = userRepository.findByEmail(principal.getUsername())
                .orElseThrow();

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        boolean isAdmin = caller.getRole() == User.UserRole.ADMIN;
        boolean isOwner = review.getUser().getId().equals(caller.getId());
        boolean isSeller = caller.getRole() == User.UserRole.SELLER
                && review.getProduct().getSeller().getUser().getId().equals(caller.getId());

        if (!isAdmin && !isOwner && !isSeller) {
            throw new AccessDeniedException("You do not have permission to delete this review");
        }

        reviewRepository.delete(review);
        return ResponseEntity.noContent().build();
    }

    // ── Admin: hide/unhide a review ─────────────────────────────────────────

    @Operation(summary = "Toggle review visibility (ADMIN only)")
    @PutMapping("/{reviewId}/hide")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> toggleHide(
            @PathVariable Long reviewId,
            @RequestBody Map<String, Boolean> body) {
        return reviewRepository.findById(reviewId).map(r -> {
            r.setHidden(body.getOrDefault("hidden", true));
            reviewRepository.save(r);
            return ResponseEntity.ok(Map.<String, Object>of("id", reviewId, "hidden", r.isHidden()));
        }).orElse(ResponseEntity.notFound().build());
    }
}
