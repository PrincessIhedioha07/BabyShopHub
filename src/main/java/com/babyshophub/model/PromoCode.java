package com.babyshophub.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", columnDefinition = "discount_type")
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Builder.Default
    @Column(name = "min_order", nullable = false, precision = 10, scale = 2)
    private BigDecimal minOrder = BigDecimal.ZERO;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Builder.Default
    @Column(name = "uses_count", nullable = false)
    private Integer usesCount = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public boolean isValid(BigDecimal orderTotal) {
        if (!active)
            return false;
        if (expiresAt != null && expiresAt.isBefore(LocalDateTime.now()))
            return false;
        if (maxUses != null && usesCount >= maxUses)
            return false;
        return orderTotal.compareTo(minOrder) >= 0;
    }

    public BigDecimal calculateDiscount(BigDecimal subtotal) {
        if (discountType == DiscountType.PERCENT) {
            return subtotal.multiply(discountValue).divide(java.math.BigDecimal.valueOf(100));
        }
        return discountValue.min(subtotal);
    }

    public enum DiscountType {
        PERCENT, FIXED
    }
}
