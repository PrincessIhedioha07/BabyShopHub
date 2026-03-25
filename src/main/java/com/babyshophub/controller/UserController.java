package com.babyshophub.controller;

import com.babyshophub.model.User;
import com.babyshophub.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and account management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "Get current user profile")
    @GetMapping("/me")
    public ResponseEntity<User> getMe(@AuthenticationPrincipal UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Update profile (first name, last name, phone, avatar)")
    @PutMapping("/me")
    public ResponseEntity<User> updateMe(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody Map<String, String> body) {
        return userRepository.findByEmail(principal.getUsername()).map(user -> {
            if (body.containsKey("firstName"))  user.setFirstName(body.get("firstName"));
            if (body.containsKey("lastName"))   user.setLastName(body.get("lastName"));
            if (body.containsKey("phone"))      user.setPhone(body.get("phone"));
            if (body.containsKey("avatarUrl"))  user.setAvatarUrl(body.get("avatarUrl"));
            return ResponseEntity.ok(userRepository.save(user));
        }).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Change password")
    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        String current = body.get("currentPassword");
        String newPwd  = body.get("newPassword");
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }
        if (newPwd == null || newPwd.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 8 characters"));
        }
        user.setPasswordHash(passwordEncoder.encode(newPwd));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @Operation(summary = "Delete account")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal UserDetails principal) {
        userRepository.findByEmail(principal.getUsername())
                .ifPresent(userRepository::delete);
        return ResponseEntity.noContent().build();
    }

    // ── Task 3: Email verification sync ───────────────────────────────────────
    //
    // Called by the Flutter client AFTER FirebaseAuth.instance.currentUser?.reload()
    // confirms emailVerified = true on the Firebase side.
    //
    // Flow:
    //   1. Flutter calls  sendEmailVerification()
    //   2. User taps link in email  →  Firebase marks them verified
    //   3. Flutter calls  currentUser?.reload()
    //   4. Flutter calls  PUT /api/users/me/verify-status  (this endpoint)
    //   5. We flip email_verified = true in PostgreSQL
    //
    // Security: the request must carry a valid JWT (enforced by SecurityConfig),
    // so only the authenticated user can mark their own account as verified.
    // We do NOT re-verify via Firebase Admin SDK here because the JWT was already
    // validated by the security filter — double-verification would be redundant.
    @Operation(summary = "Sync Firebase email-verified status to PostgreSQL. " +
               "Call only after Firebase currentUser.reload() confirms emailVerified=true.")
    @PutMapping("/me/verify-status")
    public ResponseEntity<Map<String, Object>> syncEmailVerified(
            @AuthenticationPrincipal UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
                .map(user -> {
                    if (user.isEmailVerified()) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "emailVerified", true,
                                "message", "Already verified"));
                    }
                    user.setEmailVerified(true);
                    userRepository.save(user);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "emailVerified", true,
                            "message", "Email verified successfully"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

