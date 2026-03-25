package com.babyshophub.service;

import com.babyshophub.model.User;
import com.babyshophub.model.VerificationToken;
import com.babyshophub.model.VerificationToken.Purpose;
import com.babyshophub.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final int OTP_DIGITS = 6;
    private static final int OTP_TTL_MINS = 15;

    private final JavaMailSender mailSender;
    private final VerificationTokenRepository tokenRepository;
    private final SecureRandom random = new SecureRandom();

    @Value("${spring.mail.username}")
    private String fromAddress;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Generate a fresh 6-digit OTP for email verification and email it to the user.
     * Any previous EMAIL_VERIFY tokens for this user are invalidated first.
     */
    @Transactional
    public void sendVerificationOtp(User user) {
        String otp = generateOtp();
        persist(user, otp, Purpose.EMAIL_VERIFY);
        send(user.getEmail(),
                "BabyShopHub — Verify your email",
                "Hi " + user.getFirstName() + ",\n\n"
                        + "Your verification code is: " + otp + "\n\n"
                        + "It expires in " + OTP_TTL_MINS + " minutes.\n\n"
                        + "— BabyShopHub Team");
    }

    /**
     * Generate a fresh 6-digit OTP for password reset and email it.
     * Silently no-ops if the email does not exist (caller should not reveal this).
     */
    @Transactional
    public void sendPasswordResetOtp(User user) {
        String otp = generateOtp();
        persist(user, otp, Purpose.PASSWORD_RESET);
        send(user.getEmail(),
                "BabyShopHub — Password Reset",
                "Hi " + user.getFirstName() + ",\n\n"
                        + "Your password reset code is: " + otp + "\n\n"
                        + "It expires in " + OTP_TTL_MINS
                        + " minutes. If you did not request this, ignore this email.\n\n"
                        + "— BabyShopHub Team");
    }

    /**
     * Validate an OTP. Returns the token entity if valid, throws otherwise.
     *
     * @throws IllegalArgumentException if invalid or expired
     */
    @Transactional
    public VerificationToken validate(String email, String otp, Purpose purpose) {
        VerificationToken vt = tokenRepository.findValid(email, otp, purpose)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired OTP"));
        if (vt.isExpired()) {
            throw new IllegalArgumentException("OTP has expired — please request a new one");
        }
        vt.setUsed(true);
        tokenRepository.save(vt);
        return vt;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String generateOtp() {
        int bound = (int) Math.pow(10, OTP_DIGITS);
        return String.format("%0" + OTP_DIGITS + "d", random.nextInt(bound));
    }

    private void persist(User user, String otp, Purpose purpose) {
        // Invalidate any outstanding tokens for this user+purpose
        tokenRepository.invalidatePrevious(user.getEmail(), purpose);

        VerificationToken token = VerificationToken.builder()
                .user(user)
                .token(otp)
                .purpose(purpose)
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_TTL_MINS))
                .build();
        tokenRepository.save(token);
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("OTP email sent to {}", to);
        } catch (Exception e) {
            // Log but do NOT surface SMTP errors to the caller —
            // the token is still saved so the flow can continue in dev mode.
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
        }
    }
}
