package com.babyshophub.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerProfile seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @Column(unique = true, length = 100)
    private String sku;

    @Builder.Default
    @Column(name = "stock_qty", nullable = false)
    private Integer stockQty = 0;

    @Builder.Default
    @Column(name = "low_stock_threshold", nullable = false)
    private Integer lowStockThreshold = 5;

    @Column(name = "age_suitability", length = 100)
    private String ageSuitability;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, String>> images;

    @Builder.Default
    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM) // Hibernate 6: bind as native PG enum, not VARCHAR
    @Column(columnDefinition = "product_status")
    private ProductStatus status = ProductStatus.LIVE;

    @Builder.Default
    @Column(name = "rating_avg", precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "review_count")
    private Integer reviewCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ProductStatus {
        LIVE, DRAFT, DEACTIVATED
    }

    public boolean isLowStock() {
        return stockQty > 0 && stockQty <= lowStockThreshold;
    }

    public boolean isOutOfStock() {
        return stockQty <= 0;
    }
}
