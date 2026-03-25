package com.babyshophub.repository;

import com.babyshophub.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Public listing — hides moderated reviews. */
    Page<Review> findByProductIdAndHiddenFalse(Long productId, Pageable pageable);

    /** Customer ownership check: only the author can delete their own review. */
    Optional<Review> findByIdAndUserId(Long id, Long userId);

    /** Seller check: find a review on a product owned by the seller. */
    Optional<Review> findByIdAndProductSellerUserEmail(Long id, String sellerEmail);

    /** Count non-hidden reviews per product (used for rating recalculation). */
    long countByProductIdAndHiddenFalse(Long productId);
}
