package com.babyshophub.service;

import com.babyshophub.dto.auth.RegisterRequest;
import com.babyshophub.model.SellerProfile;
import com.babyshophub.model.User;
import com.babyshophub.model.User.UserRole;
import com.babyshophub.model.VerificationToken;
import com.babyshophub.repository.SellerProfileRepository;
import com.babyshophub.repository.UserRepository;
import com.babyshophub.security.jwt.JwtService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final OtpService otpService;

    @Value("${app.admin.access-code}")
    private String adminAccessCode;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    // ── Register ─────────────────────────────────────────────────────────────

    /**
     * Creates the account and dispatches an OTP email.
     * Does NOT issue a JWT — the client must call /verify-otp first.
     */
    @Transactional
    public Map<String, Object> register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (req.getRole() == UserRole.ADMIN) {
            if (req.getAdminAccessCode() == null ||
                    !req.getAdminAccessCode().equals(adminAccessCode)) {
                throw new SecurityException("Invalid admin access code");
            }
        }

        if (req.getRole() == UserRole.SELLER &&
                (req.getStoreName() == null || req.getStoreName().isBlank())) {
            throw new IllegalArgumentException("Store name is required for seller registration");
        }

        User user = User.builder()
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole())
                .emailVerified(false)
                .build();

        user = userRepository.save(user);

        if (req.getRole() == UserRole.SELLER) {
            sellerProfileRepository.save(SellerProfile.builder()
                    .user(user)
                    .storeName(req.getStoreName())
                    .build());
        }

        // Send OTP — SMTP errors are logged but not surfaced
        otpService.sendVerificationOtp(user);

        return Map.of(
                "message", "Registration successful. Check your email for the verification code.",
                "email", user.getEmail());
    }

    // ── Verify OTP (email verification — registration flow) ──────────────────

    /**
     * Validates the 6-digit OTP emailed on registration.
     * Marks emailVerified=true and returns a JWT so the user is immediately logged
     * in.
     */
    @Transactional
    public Map<String, Object> verifyOtp(String email, String otp) {
        // validate() marks the token as used inside a transaction
        VerificationToken vt = otpService.validate(email, otp, VerificationToken.Purpose.EMAIL_VERIFY);

        User user = vt.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        return issueTokenResponse(user);
    }

    // ── Email re-verification (profile screen — authenticated) ────────────────

    /**
     * Generates and sends a 6-digit OTP to the currently logged-in user.
     * Used from the Profile screen when the user taps "Email Unverified".
     * Any previous EMAIL_VERIFY tokens for this user are invalidated first (handled
     * inside OtpService.sendVerificationOtp).
     *
     * @param email the principal email from the JWT
     */
    @Transactional
    public void sendEmailVerificationOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
        if (user.isEmailVerified()) {
            // Idempotent: silently succeed if already verified
            return;
        }
        otpService.sendVerificationOtp(user);
    }

    /**
     * Validates the 6-digit OTP for a logged-in user and marks their email
     * as verified in PostgreSQL. Does NOT issue a new JWT — the frontend
     * simply pops the OTP screen back to the profile page.
     *
     * @param email the principal email from the JWT
     * @param otp   the 6-digit code entered by the user
     * @throws IllegalArgumentException if the OTP is invalid or expired
     * @throws ResponseStatusException  if the user is not found
     */
    @Transactional
    public void verifyEmailOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        // Reuse OtpService.validate() — marks the token as used automatically
        otpService.validate(email, otp, VerificationToken.Purpose.EMAIL_VERIFY);

        user.setEmailVerified(true);
        userRepository.save(user);
        log.info("Email verified for user {} via profile OTP", user.getEmail());
    }

    // ── Resend Verification OTP (pre-login escape hatch) ──────────────────────

    /**
     * Generates and sends a fresh EMAIL_VERIFY OTP for a user who registered
     * but never verified (or whose OTP expired).
     *
     * Security: Always succeeds silently — callers cannot tell whether the email
     * exists or is already verified, preventing enumeration attacks.
     *
     * @param email address supplied by the Flutter login screen
     */
    @Transactional
    public void resendVerificationOtp(String email) {
        if (email == null || email.isBlank())
            return;

        userRepository.findByEmail(email.trim().toLowerCase()).ifPresent(user -> {
            // Only resend if the account is genuinely unverified
            if (!user.isEmailVerified()) {
                otpService.sendVerificationOtp(user); // invalidates old tokens internally
                log.info("Verification OTP re-sent to {} (escape hatch)", email);
            }
        });
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (user.isAccountLocked()) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    "Account locked. Try again after " + user.getLockedUntil());
        }
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has been suspended");
        }
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Email not verified. Check your inbox for the OTP code.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
        } catch (BadCredentialsException e) {
            handleFailedLogin(user);
            throw new BadCredentialsException("Invalid email or password");
        }

        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        return issueTokenResponse(user);
    }

    // ── Firebase Login (Social / Phone Auth handshake) ─────────────────────────

    /**
     * Validates a Firebase ID token issued by Google / Apple / Phone Auth on the
     * Flutter client, then returns our own application JWT.
     *
     * <p>
     * If no local account exists for the Firebase-verified email, one is
     * auto-created with role=CUSTOMER and emailVerified=true.
     * </p>
     *
     * @param firebaseIdToken the raw Firebase ID token from the Flutter client
     * @return the same {@link Map} shape returned by {@link #login}
     */
    @Transactional
    public Map<String, Object> firebaseLogin(String firebaseIdToken) {
        // 1. Guard: Firebase Admin SDK must be initialised
        if (com.google.firebase.FirebaseApp.getApps().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Firebase Admin SDK is not configured on this server");
        }

        // 2. Verify the token cryptographically with Google's public keys
        FirebaseToken decodedToken;
        try {
            decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseIdToken);
        } catch (FirebaseAuthException e) {
            log.warn("[Firebase] Token verification failed: {}", e.getAuthErrorCode());
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid or expired Firebase ID token");
        }

        // 3. Extract claims — Firebase guarantees email is verified for Google/Apple
        String email = decodedToken.getEmail();
        String name = decodedToken.getName(); // "John Doe" or null
        String picture = decodedToken.getPicture(); // avatar URL or null
        String uid = decodedToken.getUid();

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Firebase token does not contain an email address. " +
                            "Enable email scope in your OAuth provider.");
        }

        log.info("[Firebase] Token verified for uid={} email={}", uid, email);

        // 4. Find-or-create the local user
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> autoRegisterFirebaseUser(email, name, picture));

        // 5. Guard: suspended accounts cannot log in even via Firebase
        if (user.isSuspended()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This account has been suspended.");
        }

        // 6. Issue our own JWT (same shape as /login)
        return issueTokenResponse(user);
    }

    /**
     * Creates a minimal User record for social-auth registrations.
     * <ul>
     * <li>emailVerified = true (Firebase already verified the email)</li>
     * <li>role = CUSTOMER (social logins are always customers)</li>
     * <li>passwordHash = random (user will never use password login)</li>
     * </ul>
     */
    private User autoRegisterFirebaseUser(String email, String fullName, String avatarUrl) {
        String firstName = "";
        String lastName = "";
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().split(" ", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }

        User newUser = User.builder()
                .email(email)
                // Random irreversible hash — social users never use password login
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .firstName(firstName.isBlank() ? email.split("@")[0] : firstName)
                .lastName(lastName)
                .avatarUrl(avatarUrl)
                .role(UserRole.CUSTOMER)
                .emailVerified(true)
                .build();

        newUser = userRepository.save(newUser);
        log.info("[Firebase] Auto-registered new user id={} email={}", newUser.getId(), email);
        return newUser;
    }

    // ── Forgot Password ────────────────────────────────────────────────────────

    /**
     * Sends a password-reset OTP. Always returns 200 — never reveals if the email
     * exists.
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email)
                .ifPresent(otpService::sendPasswordResetOtp);
        // Silently no-op for unknown emails to prevent user enumeration
    }

    // ── Reset Password ─────────────────────────────────────────────────────────

    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        VerificationToken vt = otpService.validate(email, otp, VerificationToken.Purpose.PASSWORD_RESET);
        User user = vt.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }
        userRepository.save(user);
    }

    private Map<String, Object> issueTokenResponse(User user) {
        UserDetails details = userDetailsService.loadUserByUsername(user.getEmail());
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId());

        String accessToken = jwtService.generateAccessToken(details, claims);
        String refreshToken = jwtService.generateRefreshToken(details);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "tokenType", "Bearer",
                "user", buildUserResponse(user));
    }

    private Map<String, Object> buildUserResponse(User user) {
        return Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "role", user.getRole().name(),
                "emailVerified", user.isEmailVerified(),
                "avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
    }
}
