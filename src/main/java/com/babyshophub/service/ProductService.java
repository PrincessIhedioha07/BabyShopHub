package com.babyshophub.service;

import com.babyshophub.model.Category;
import com.babyshophub.model.Product;
import com.babyshophub.repository.CategoryRepository;
import com.babyshophub.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public Page<Product> getProducts(String categorySlug, boolean featuredOnly, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (featuredOnly) {
            return productRepository.findByFeaturedTrueAndActiveTrueAndStatus(Product.ProductStatus.LIVE, pageable);
        }
        return productRepository.findByActiveTrueAndStatus(Product.ProductStatus.LIVE, pageable);
    }

    public Product getById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    public Product getBySlug(String slug) {
        return productRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Product not found: " + slug));
    }

    public Page<Product> search(String q, String categorySlug, String sort,
            BigDecimal minPrice, BigDecimal maxPrice,
            Boolean inStock, int page, int size) {
        Sort sorting = switch (sort != null ? sort : "relevance") {
            case "price_asc" -> Sort.by("price").ascending();
            case "price_desc" -> Sort.by("price").descending();
            case "newest" -> Sort.by("createdAt").descending();
            case "rating" -> Sort.by("ratingAvg").descending();
            default -> Sort.by("ratingAvg").descending();
        };
        Pageable pageable = PageRequest.of(page, size, sorting);
        return productRepository.searchProducts(q, categorySlug, minPrice, maxPrice, inStock, pageable);
    }

    public List<String> autocomplete(String prefix) {
        return productRepository.findSuggestions(prefix);
    }

    public List<Category> getAllCategories() {
        return categoryRepository.findAll(Sort.by("sortOrder").ascending());
    }
}
