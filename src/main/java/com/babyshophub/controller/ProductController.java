package com.babyshophub.controller;

import com.babyshophub.model.Category;
import com.babyshophub.model.Product;
import com.babyshophub.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Browse, search, and get product details")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "List products with optional category filter and pagination")
    @GetMapping
    public ResponseEntity<Page<Product>> getProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "false") boolean featured,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.getProducts(category, featured, page, size));
    }

    @Operation(summary = "Get product by ID")
    @GetMapping("/{id}")
    public ResponseEntity<Product> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @Operation(summary = "Search products with full-text and filters")
    @GetMapping("/search")
    public ResponseEntity<Page<Product>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(productService.search(q, category, sort, minPrice, maxPrice, inStock, page, size));
    }

    @Operation(summary = "Autocomplete suggestions for search bar")
    @GetMapping("/search/suggest")
    public ResponseEntity<List<String>> suggest(@RequestParam(name = "q") String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(productService.autocomplete(q));
    }

    @Operation(summary = "Get trending products and tags")
    @GetMapping("/trending")
    public ResponseEntity<List<Map<String, String>>> trending() {
        return ResponseEntity.ok(List.of(
                Map.of("label", "🍼 Diapers", "query", "diapers"),
                Map.of("label", "🌿 Organic", "query", "organic"),
                Map.of("label", "🍶 Feeding", "query", "feeding"),
                Map.of("label", "👕 Clothing", "query", "clothing"),
                Map.of("label", "🛡️ Safety", "query", "safety"),
                Map.of("label", "🧸 Toys", "query", "toys"),
                Map.of("label", "🛁 Bath", "query", "bath"),
                Map.of("label", "🌙 Nursery", "query", "nursery")
        ));
    }

    @Operation(summary = "Get all categories")
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getCategories() {
        return ResponseEntity.ok(productService.getAllCategories());
    }
}
