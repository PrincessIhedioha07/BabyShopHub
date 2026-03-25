package com.babyshophub.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for POST /api/auth/firebase-login.
 *
 * The Flutter client obtains this token from:
 * FirebaseAuth.instance.currentUser?.getIdToken()
 *
 * The backend verifies it using the Firebase Admin SDK
 * (FirebaseAuth.getInstance().verifyIdToken()) and then
 * issues our own application JWT.
 */
@Data
public class FirebaseLoginRequest {

    @NotBlank(message = "Firebase ID token is required")
    private String firebaseIdToken;
}
