package com.babyshophub.repository;

import com.babyshophub.model.Category;
import com.babyshophub.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Product> findByActiveTrueAndStatus(Product.ProductStatus status, Pageable pageable);

    Page<Product> findByFeaturedTrueAndActiveTrueAndStatus(Product.ProductStatus status, Pageable pageable);

    // Native query — PostgreSQL full-text search with @@/plainto_tsquery is not
    // valid JPQL.
    // A countQuery is required alongside a paginated native query.
    @Query(value = """
            SELECT * FROM products p
            WHERE p.is_active = true AND p.status = 'LIVE'
            AND (CAST(:q AS text) IS NULL
                 OR p.search_vector @@ plainto_tsquery('english', :q)
                 OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
            AND (CAST(:categorySlug AS text) IS NULL
                 OR p.category_id = (SELECT id FROM categories WHERE slug = :categorySlug))
            AND (CAST(:minPrice AS numeric) IS NULL OR p.price >= CAST(:minPrice AS numeric))
            AND (CAST(:maxPrice AS numeric) IS NULL OR p.price <= CAST(:maxPrice AS numeric))
            AND (CAST(:inStock AS boolean) IS NULL OR (:inStock = true AND p.stock_qty > 0))
            ORDER BY p.rating_avg DESC
            """, countQuery = """
            SELECT COUNT(*) FROM products p
            WHERE p.is_active = true AND p.status = 'LIVE'
            AND (CAST(:q AS text) IS NULL
                 OR p.search_vector @@ plainto_tsquery('english', :q)
                 OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')))
            AND (CAST(:categorySlug AS text) IS NULL
                 OR p.category_id = (SELECT id FROM categories WHERE slug = :categorySlug))
            AND (CAST(:minPrice AS numeric) IS NULL OR p.price >= CAST(:minPrice AS numeric))
            AND (CAST(:maxPrice AS numeric) IS NULL OR p.price <= CAST(:maxPrice AS numeric))
            AND (CAST(:inStock AS boolean) IS NULL OR (:inStock = true AND p.stock_qty > 0))
            """, nativeQuery = true)
    Page<Product> searchProducts(
            @Param("q") String q,
            @Param("categorySlug") String categorySlug,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("inStock") Boolean inStock,
            Pageable pageable);

    // Native query — LIMIT is not valid in JPQL.
    @Query(value = "SELECT DISTINCT p.name FROM products p WHERE LOWER(p.name) LIKE LOWER(CONCAT(:prefix, '%')) AND p.is_active = true LIMIT 10", nativeQuery = true)
    List<String> findSuggestions(@Param("prefix") String prefix);

    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    // Replaced derived method (Hibernate struggles with multi-join derivation) with
    // explicit JPQL.
    @Query("SELECT p FROM Product p WHERE p.category = :category AND p.active = true ORDER BY p.ratingAvg DESC")
    List<Product> findTop5ByCategoryAndActiveTrue(@Param("category") Category category, Pageable pageable);
}
