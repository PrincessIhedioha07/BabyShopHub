package com.babyshophub.controller;

import com.babyshophub.dto.auth.FirebaseLoginRequest;
import com.babyshophub.dto.auth.RegisterRequest;
import com.babyshophub.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Sign up, OTP verification, sign in, password reset")
public class AuthController {

    private final AuthService authService;

    // ── Registration ─────────────────────────────────────────────────────────

    @Operation(summary = "Register a new account. Returns {message, email} — no token yet.")
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    // ── OTP Verification (registration flow) ─────────────────────────────────

    @Operation(summary = "Verify email with OTP code (registration). Returns JWT on success.")
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String otp = body.get("otp");
        if (email == null || otp == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "email and otp are required"));
        }
        return ResponseEntity.ok(authService.verifyOtp(email, otp));
    }

    // ── Profile email re-verification (authenticated) ─────────────────────────
    //
    // These two endpoints are used from the Profile screen when a logged-in user
    // wants to verify their email address for the first time (e.g. after a
    // Firebase sign-in that skipped the registration OTP flow).
    //
    // Flow:
    // 1. POST /api/auth/send-verification-otp → OTP e-mailed to user
    // 2. User enters the 6-digit code
    // 3. POST /api/auth/verify-email-otp → email_verified = true in DB
    // 4. Flutter pops the OTP screen and updates the profile badge to ✓

    @Operation(summary = "Send a 6-digit OTP to the authenticated user's email for verification.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/send-verification-otp")
    public ResponseEntity<Map<String, String>> sendVerificationOtp(
            @AuthenticationPrincipal UserDetails principal) {
        authService.sendEmailVerificationOtp(principal.getUsername());
        return ResponseEntity.ok(Map.of(
                "message", "Verification code sent. Check your inbox."));
    }

    @Operation(summary = "Verify a 6-digit OTP for a logged-in user. " +
            "Marks email_verified=true. Does NOT issue a new JWT.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/verify-email-otp")
    public ResponseEntity<Map<String, Object>> verifyEmailOtp(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody Map<String, String> body) {
        String otp = body.get("otp");
        if (otp == null || otp.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "otp is required"));
        }
        authService.verifyEmailOtp(principal.getUsername(), otp);
        return ResponseEntity.ok(Map.of("verified", true,
                "message", "Email verified successfully."));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Login with email + password. Email must be verified first.")
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.login(body.get("email"), body.get("password")));
    }

    // ── Firebase / Social Login ──────────────────────────────────────────────────

    @Operation(summary = "Exchange a Firebase ID token (Google/Apple/Phone) for an app JWT. " +
            "Creates a local account automatically if none exists.")
    @PostMapping("/firebase-login")
    public ResponseEntity<Map<String, Object>> firebaseLogin(
            @Valid @RequestBody FirebaseLoginRequest request) {
        return ResponseEntity.ok(authService.firebaseLogin(request.getFirebaseIdToken()));
    }

    // ── Resend OTP ────────────────────────────────────────────────────────────
    //
    // Called by the Flutter login screen when the backend returns 403
    // "Email not verified". Generates a fresh EMAIL_VERIFY OTP and emails it.
    // Always returns 200 to prevent email enumeration attacks.

    @Operation(summary = "Resend email verification OTP (pre-login, no auth required). "
            + "Safely no-ops if user doesn't exist or is already verified.")
    @PostMapping("/resend-otp")
    public ResponseEntity<Map<String, String>> resendOtp(
            @RequestBody Map<String, String> body) {
        authService.resendVerificationOtp(body.getOrDefault("email", ""));
        return ResponseEntity.ok(Map.of("message",
                "If that email exists and is unverified, a new OTP has been sent."));
    }

    // ── Forgot / Reset Password ───────────────────────────────────────────────

    @Operation(summary = "Send a password-reset OTP to the given email.")
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) {
        authService.forgotPassword(body.getOrDefault("email", ""));
        return ResponseEntity.ok(Map.of("message",
                "If that email exists, you'll receive a reset code shortly."));
    }

    @Operation(summary = "Reset password with OTP. Body: {email, otp, newPassword}")
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req.email(), req.otp(), req.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now log in."));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Operation(summary = "Logout — client should discard the token (stateless JWT).")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    record ResetPasswordRequest(
            @Email @NotBlank String email,
            @NotBlank String otp,
            @NotBlank @Size(min = 8) String newPassword) {
    }
}
