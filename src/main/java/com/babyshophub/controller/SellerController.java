package com.babyshophub.controller;

import com.babyshophub.model.Product;
import com.babyshophub.repository.ProductRepository;
import com.babyshophub.repository.SellerProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.babyshophub.repository.CategoryRepository;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
@Tag(name = "Seller", description = "Seller portal — products, orders, analytics, payouts")
@SecurityRequirement(name = "bearerAuth")
public class SellerController {

        private final SellerProfileRepository sellerProfileRepository;
        private final ProductRepository productRepository;
        private final CategoryRepository categoryRepository;

        @Operation(summary = "Seller dashboard overview")
        @GetMapping("/dashboard")
        public ResponseEntity<Map<String, Object>> dashboard(
                        @AuthenticationPrincipal UserDetails principal) {
                return sellerProfileRepository.findByUserEmail(principal.getUsername())
                                .map(seller -> {
                                        long products = productRepository.findBySellerId(
                                                        seller.getId(), PageRequest.of(0, 1)).getTotalElements();
                                        return ResponseEntity.ok(Map.<String, Object>of(
                                                        "storeName", seller.getStoreName(),
                                                        "rating", seller.getRatingAvg(),
                                                        "reviewCount", seller.getReviewCount(),
                                                        "productCount", products,
                                                        "totalRevenue", 0.0,
                                                        "isVerified", seller.isVerified()));
                                })
                                .orElse(ResponseEntity.notFound().build());
        }

        @Operation(summary = "List seller's products")
        @GetMapping("/products")
        public ResponseEntity<Page<Product>> myProducts(
                        @AuthenticationPrincipal UserDetails principal,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                return sellerProfileRepository.findByUserEmail(principal.getUsername())
                                .map(seller -> ResponseEntity.ok(
                                                productRepository.findBySellerId(
                                                                seller.getId(),
                                                                PageRequest.of(page, size,
                                                                                Sort.by("createdAt").descending()))))
                                .orElse(ResponseEntity.notFound().build());
        }

        @Operation(summary = "Create new product listing")
        @PostMapping("/products")
        public ResponseEntity<Product> createProduct(
                        @AuthenticationPrincipal UserDetails principal,
                        @RequestBody Product product) {
                return sellerProfileRepository.findByUserEmail(principal.getUsername())
                                .map(seller -> {
                                        product.setSeller(seller);
                                        if (product.getCategory() != null && product.getCategory().getSlug() != null) {
                                            categoryRepository.findBySlug(product.getCategory().getSlug())
                                                    .ifPresent(product::setCategory);
                                        }
                                        if (product.getName() != null) {
                                            String baseSlug = product.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
                                            if (baseSlug.isEmpty()) baseSlug = "product";
                                            String slug = baseSlug;
                                            int counter = 1;
                                            while (productRepository.existsBySlug(slug)) {
                                                slug = baseSlug + "-" + counter++;
                                            }
                                            product.setSlug(slug);
                                        }
                                        return ResponseEntity.ok(productRepository.save(product));
                                })
                                .orElse(ResponseEntity.notFound().build());
        }

        @Operation(summary = "Update product listing")
        @PutMapping("/products/{id}")
        public ResponseEntity<Product> updateProduct(
                        @AuthenticationPrincipal UserDetails principal,
                        @PathVariable Long id,
                        @RequestBody Product updates) {
                return productRepository.findById(id)
                                .filter(p -> p.getSeller().getUser().getEmail().equals(principal.getUsername()))
                                .map(p -> {
                                        p.setName(updates.getName());
                                        p.setDescription(updates.getDescription());
                                        p.setPrice(updates.getPrice());
                                        p.setStockQty(updates.getStockQty());
                                        p.setStatus(updates.getStatus());
                                        return ResponseEntity.ok(productRepository.save(p));
                                })
                                .orElse(ResponseEntity.notFound().build());
        }

        @Operation(summary = "Delete product listing")
        @DeleteMapping("/products/{id}")
        public ResponseEntity<Void> deleteProduct(
                        @AuthenticationPrincipal UserDetails principal,
                        @PathVariable Long id) {
                productRepository.findById(id)
                                .filter(p -> p.getSeller().getUser().getEmail().equals(principal.getUsername()))
                                .ifPresent(productRepository::delete);
                return ResponseEntity.noContent().build();
        }

        @Operation(summary = "Seller analytics — views, conversions, revenue")
        @GetMapping("/analytics")
        public ResponseEntity<Map<String, Object>> analytics(
                        @AuthenticationPrincipal UserDetails principal) {
                return ResponseEntity.ok(Map.of(
                                "message", "Analytics data — connect to real data pipeline in production",
                                "revenue30d", 0,
                                "orders30d", 0,
                                "views30d", 0));
        }
}
