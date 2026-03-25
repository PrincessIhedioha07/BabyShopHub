package com.babyshophub.repository;

import com.babyshophub.model.VerificationToken;
import com.babyshophub.model.VerificationToken.Purpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    /** Look up a valid (unused, matching purpose) token by email. */
    @Query("""
                SELECT vt FROM VerificationToken vt
                JOIN vt.user u
                WHERE u.email = :email
                  AND vt.token = :token
                  AND vt.purpose = :purpose
                  AND vt.used = false
            """)
    Optional<VerificationToken> findValid(
            @Param("email") String email,
            @Param("token") String token,
            @Param("purpose") Purpose purpose);

    /**
     * Invalidate all previous tokens for a user+purpose before issuing a new one.
     */
    @Modifying
    @Transactional
    @Query("""
                UPDATE VerificationToken vt
                SET vt.used = true
                WHERE vt.user.email = :email AND vt.purpose = :purpose
            """)
    void invalidatePrevious(@Param("email") String email, @Param("purpose") Purpose purpose);
}
