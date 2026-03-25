package com.babyshophub.dto.auth;

import com.babyshophub.model.User.UserRole;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100)
    private String lastName;

    @NotBlank @Email(message = "Invalid email address")
    private String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotNull
    private UserRole role = UserRole.CUSTOMER;

    // Seller only
    private String storeName;

    // Admin only
    private String adminAccessCode;
}
