package com.learningLadder.learningLadder.entity.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.learningLadder.learningLadder.enums.accountStatus.AccountStatus;
import com.learningLadder.learningLadder.enums.userRole.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity(
        name = "users"
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "users",
        indexes = {

        }
)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "password")
    private String password;

    @Builder.Default
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.FINANCE_OFFICER;

    @Builder.Default
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private AccountStatus status = AccountStatus.UNVERIFIED;

    @Column(name = "failedLoginAttempts")
    private int failedLoginAttempts;

    @Column(name = "forgot_password")
    private String forgotPassword;

    @Column
    private Instant forgotPasswordExpirationDate;

    @Column(name = "reset_password")
    private String resetPassword;

    @Column(name = "otpCode")
    private String otpCode;

    @Column(name = "otpCodeExpirationCode")
    private Instant optCodeExpirationDate;

    @Column(name = "magicLinkToken")
    private String magicLinkToken;

    @Column(name = "verifyMagicLinkToken")
    private String verifyMagicLinkToken;

    @Column(name = "magicLinkTokenExpirationDate")
    private Instant magicLinkTokenExpirationDate;

    @JsonIgnore
    @Column(name = "isAccountBlocked")
    private boolean isAccountBlocked = false;

    @JsonIgnore
    @Column(name = "isAccountUnBlocked")
    private boolean isAccountUnBlocked = false;

    @Column(name = "isAccountVerified")
    private boolean isAccountVerified = false;

    @Column(name = "isAccountDeleted")
    private boolean isAccountDeleted = false;

    @Column(name = "otpCodeVerified")
    private boolean optCodeVerified = false;

    @Column(name = "firebase_uid", unique = true, length = 128)
    private String firebaseUid;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "accessToken")
    private String accessToken;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "refreshToken")
    private String refreshToken;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void normalize() {
        if (email != null) {
            email = email.trim().toLowerCase();
        }
    }
}
