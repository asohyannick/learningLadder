package com.learningLadder.learningLadder.controller.userController;

import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.apiResponse.APISuccessResponse;
import com.learningLadder.learningLadder.dto.userDto.*;
import com.learningLadder.learningLadder.service.userService.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/${app.api.version}/auth")
@Tag(name = "Authentication and Authorization Endpoints",
        description = "Registration, verification, login, tokens, password reset and account management")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new account",
            description = "Creates an UNVERIFIED account with the STUDENT role and emails a 6-digit verification "
                    + "code. No tokens are issued here: the user must verify the code and then log in."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Account created and verification email queued",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or the email is already registered",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Email already registered", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "BAD_REQUEST",
                                      "message": "An account with this email already exists",
                                      "path": "/api/v1/auth/register",
                                      "timestamp": "2026-11-08T00:15:00Z"
                                    }
                                    """)))
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> register(
            @Valid @RequestBody RegisterRequestDto registerRequestDto,
            HttpServletRequest request) {

        UserResponseDto created = userService.register(registerRequestDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(APISuccessResponse.created(
                "Account created successfully. We've sent a 6-digit verification code to your email. "
                        + "Enter it to activate your account.",
                created,
                request.getRequestURI()));
    }

    @PostMapping("/verify-otp")
    @Operation(
            summary = "Verify email with the OTP code",
            description = "Confirms the 6-digit code sent by email and marks the account as VERIFIED. "
                    + "No tokens are issued here: the user logs in afterwards."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Email verified and account activated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid or expired code, already verified, or account blocked",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Invalid code", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "BAD_REQUEST",
                                      "message": "Invalid or expired verification code",
                                      "path": "/api/v1/auth/verify-otp",
                                      "timestamp": "2026-11-08T00:20:00Z"
                                    }
                                    """)))
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> verifyOtpCode(
            @Valid @RequestBody VerifyOtpCodeDto verifyOtpCodeDto,
            HttpServletRequest request) {

        UserResponseDto verified = userService.verifyOtpCode(verifyOtpCodeDto);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Your email has been verified and your account is now active. You can log in.",
                verified,
                request.getRequestURI()));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Log in with email and password",
            description = "Returns the user profile together with a short-lived access token and a refresh token. "
                    + "The account must be verified and not blocked. Send the access token as "
                    + "'Authorization: Bearer <token>' on protected endpoints."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful, tokens issued",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Wrong credentials, unverified account or blocked account",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Wrong credentials", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Invalid email or password",
                                              "path": "/api/v1/auth/login",
                                              "timestamp": "2026-11-08T00:30:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Account not verified", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Please verify your email with the OTP we sent you before logging in.",
                                              "path": "/api/v1/auth/login",
                                              "timestamp": "2026-11-08T00:30:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Account blocked", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Your account has been blocked. Please contact support.",
                                              "path": "/api/v1/auth/login",
                                              "timestamp": "2026-11-08T00:30:00Z"
                                            }
                                            """)
                            }))
    })
    public ResponseEntity<APISuccessResponse<LoginResponseDto>> login(
            @Valid @RequestBody LoginRequestDto loginRequestDto,
            HttpServletRequest request) {

        LoginResponseDto loggedIn = userService.login(loginRequestDto);

        String name = loggedIn.firstName() == null || loggedIn.firstName().isBlank()
                ? "" : ", " + loggedIn.firstName();

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Welcome back" + name + "! You are now logged in.",
                loggedIn,
                request.getRequestURI()));
    }

    @PostMapping(value = "/refresh-token",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement
    @Operation(
            summary = "Get a new access token",
            description = """
                    Exchanges a valid refresh token for a new access token AND a new refresh token
                    (refresh token rotation). The refresh token you send stops working immediately:
                    always store and use the new one returned here.

                    Call this when the access token expires (HTTP 401 from other endpoints).
                    No Authorization header is needed.

                    Security: if an old (already used) refresh token is presented, the session is
                    revoked and the user must log in again, because it may have been stolen.""",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = GenerateNewAccessToken.class),
                            examples = @ExampleObject(name = "Refresh request", value = """
                                    {
                                      "refreshToken": "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiI4YzFi...signature"
                                    }
                                    """))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "New access and refresh tokens issued",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APISuccessResponse.class),
                            examples = @ExampleObject(name = "Session refreshed", value = """
                                    {
                                      "success": true,
                                      "status": 200,
                                      "message": "Your session has been refreshed. Your previous refresh token is no longer valid, so use the new one from now on.",
                                      "data": {
                                        "accessToken": "eyJhbGciOiJIUzI1NiJ9...newAccess",
                                        "accessTokenExpiresIn": 300,
                                        "accessTokenExpiresAt": "2026-11-12T21:05:00Z",
                                        "refreshToken": "eyJhbGciOiJIUzI1NiJ9...newRefresh",
                                        "refreshTokenExpiresIn": 604800,
                                        "refreshTokenExpiresAt": "2026-11-19T21:00:00Z",
                                        "tokenType": "Bearer"
                                      },
                                      "path": "/api/v1/auth/refresh-token",
                                      "timestamp": "2026-11-12T21:00:00Z"
                                    }
                                    """))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Refresh token invalid, expired, already used, or the account can no longer sign in",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Invalid or expired token", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Invalid or expired refresh token",
                                              "path": "/api/v1/auth/refresh-token",
                                              "timestamp": "2026-11-12T21:00:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Blocked or unverified account", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "This account cannot refresh its session",
                                              "path": "/api/v1/auth/refresh-token",
                                              "timestamp": "2026-11-12T21:00:00Z"
                                            }
                                            """)
                            })),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many refresh attempts from this client",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = APIErrorResponse.class)))
    })
    public ResponseEntity<APISuccessResponse<GenerateNewAccessTokenResponseDto>> generateNewAccessToken(
            @Valid @RequestParam GenerateNewAccessToken generateNewAccessToken,
            HttpServletRequest request) {

        GenerateNewAccessTokenResponseDto tokens = userService.generateNewAccessToken(generateNewAccessToken);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Your session has been refreshed. Your previous refresh token is no longer valid, "
                        + "so use the new one from now on.",
                tokens,
                request.getRequestURI()));
    }

    @PostMapping("/resend-otp")
    @Operation(
            summary = "Resend the email verification code",
            description = "Sends a new 6-digit code to an unverified account. A new code is only issued once the "
                    + "previous one has expired. If the previous code is still valid, the user is told how long "
                    + "to wait."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "New verification code queued for delivery",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Previous code still valid, account already verified, blocked or not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Previous code still valid", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Your current code is still valid. Please use it or try again in 7 minutes.",
                                              "path": "/api/v1/auth/resend-otp",
                                              "timestamp": "2026-11-08T00:50:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Already verified", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "This account is already verified",
                                              "path": "/api/v1/auth/resend-otp",
                                              "timestamp": "2026-11-08T00:50:00Z"
                                            }
                                            """)
                            }))
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> resendOtpCode(
            @Valid @RequestBody ResendOtpCodeDto resendOtpCodeDto,
            HttpServletRequest request) {

        UserResponseDto user = userService.resendOtpCode(resendOtpCodeDto);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "We've sent a new verification code to your email. Your previous codes no longer work.",
                user,
                request.getRequestURI()));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Log out and revoke the refresh token",
            description = "Requires the user's current refresh token in the request body. The token must be valid, "
                    + "unexpired and the one currently issued to the user. It is revoked, so the session can no "
                    + "longer be renewed. The access token stays valid until it expires, so clients should discard it."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Logged out; refresh token revoked",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Refresh token missing, invalid, expired or already revoked",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Expired refresh token", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Refresh token has expired",
                                              "path": "/api/v1/auth/logout",
                                              "timestamp": "2026-11-08T00:55:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Invalid refresh token", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Invalid refresh token",
                                              "path": "/api/v1/auth/logout",
                                              "timestamp": "2026-11-08T00:55:00Z"
                                            }
                                            """)
                            }))
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> logout(
            @Valid @RequestBody LogoutRequestDto logoutRequestDto,
            HttpServletRequest request) {

        UserResponseDto user = userService.logout(logoutRequestDto);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "You have been logged out successfully. See you again soon!",
                user,
                request.getRequestURI()));
    }

    // =====================================================================
    // 7. Forgot password
    // =====================================================================
    @PostMapping("/forgot-password")
    @Operation(
            summary = "Request a password reset link",
            description = "Emails a single-use reset link to a verified, unblocked account. The link expires after "
                    + "a short time, and requesting a new link invalidates the previous one."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Password reset email queued for delivery",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Account not found, not verified or blocked",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Account not found", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "No account found for this email",
                                              "path": "/api/v1/auth/forgot-password",
                                              "timestamp": "2026-11-08T01:00:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Account not verified", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Please verify your email before resetting your password.",
                                              "path": "/api/v1/auth/forgot-password",
                                              "timestamp": "2026-11-08T01:00:00Z"
                                            }
                                            """)
                            }))
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> forgotPassword(
            @Valid @RequestBody ForgotPasswordDto forgotPasswordDto,
            HttpServletRequest request) {

        UserResponseDto user = userService.forgotPassword(forgotPasswordDto);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "We've sent a password reset link to your email. The link can only be used once "
                        + "and expires shortly, so use it soon.",
                user,
                request.getRequestURI()));
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Set a new password using the emailed reset token",
            description = "Takes the token from the reset link together with the new password and its confirmation. "
                    + "The link works once. On success every existing session is signed out, so the user must "
                    + "log in again with the new password."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Password changed and all sessions signed out",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed, link invalid or expired, passwords differ, or new password "
                            + "matches the current one",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Invalid or expired link", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Invalid or expired reset link",
                                              "path": "/api/v1/auth/reset-password",
                                              "timestamp": "2026-11-08T01:05:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Same as current password", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "New password must be different from your current password",
                                              "path": "/api/v1/auth/reset-password",
                                              "timestamp": "2026-11-08T01:05:00Z"
                                            }
                                            """)
                            }))
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> resetPassword(
            @RequestParam("token")
            TokenQueryDto token,
            @Valid @RequestBody ResetPasswordDto resetPasswordDto,
            HttpServletRequest request
    ) {

        UserResponseDto user = userService.resetPassword(token, resetPasswordDto);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Your password has been changed successfully. Please log in with your new password.",
                user,
                request.getRequestURI()));
    }

    @PatchMapping("/users/{userId}/block")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','IT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Block a user account (admin only)",
            description = "Blocks the account, signs the user out by revoking their refresh token, and emails them "
                    + "the reason. Admins cannot block themselves, and super admin accounts cannot be blocked."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Account blocked and the user notified by email",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed, user not found, already blocked, or the account is protected",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Already blocked", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "This account is already blocked",
                                              "path": "/api/v1/auth/users/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42/block",
                                              "timestamp": "2026-11-08T01:10:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Cannot block yourself", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "You cannot block your own account",
                                              "path": "/api/v1/auth/users/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42/block",
                                              "timestamp": "2026-11-08T01:10:00Z"
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> blockAccount(
            @Parameter(description = "ID of the user to block", required = true)
            @PathVariable UUID userId,
            @Valid @RequestBody BlockAccountRequestDto blockAccountRequestDto,
            HttpServletRequest request) {

        UserResponseDto blocked = userService.blockAccount(userId, blockAccountRequestDto);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "The account has been blocked and the user has been notified by email.",
                blocked,
                request.getRequestURI()));
    }

    @PatchMapping("/users/{userId}/unblock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','IT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Unblock a user account (admin only)",
            description = "Restores access to a blocked account and emails the user. A user who was verified before "
                    + "being blocked returns to VERIFIED and can log in right away. A user who was never "
                    + "verified goes back to UNVERIFIED and must still complete OTP verification."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Account unblocked and the user notified by email",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed, user not found, or the account is not blocked",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "Not blocked", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "BAD_REQUEST",
                                      "message": "This account is not blocked",
                                      "path": "/api/v1/auth/users/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42/unblock",
                                      "timestamp": "2026-11-08T01:15:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> unBlockAccount(
            @Parameter(description = "ID of the user to unblock", required = true)
            @PathVariable UUID userId,
            @Valid @RequestBody UnBlockAccountRequestDto unBlockAccountRequestDto,
            HttpServletRequest request) {

        UserResponseDto unblocked = userService.unBlockAccount(userId, unBlockAccountRequestDto);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "The account has been unblocked and the user has been notified by email.",
                unblocked,
                request.getRequestURI()));
    }

    @DeleteMapping("/account")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Delete your own account",
            description = "Soft-deletes the logged-in user's account after they confirm with their password. "
                    + "All sessions and pending codes or links are cleared, and the account can no longer log in. "
                    + "Super admin accounts cannot be deleted."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Account deleted",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed, wrong password, or the account is protected",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Wrong password", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Incorrect password",
                                              "path": "/api/v1/auth/account",
                                              "timestamp": "2026-11-08T01:20:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Protected account", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "A super admin account cannot be deleted",
                                              "path": "/api/v1/auth/account",
                                              "timestamp": "2026-11-08T01:20:00Z"
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequestDto deleteAccountRequestDto,
            @RequestParam UUID userId,
            HttpServletRequest request) {

        UserResponseDto deleted = userService.deleteAccount(userId, deleteAccountRequestDto);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Your account has been deleted. We're sorry to see you go.",
                deleted,
                request.getRequestURI()));
    }

    @PostMapping("/magic-link/send")
    @Operation(
            summary = "Request a magic sign-in link",
            description = "Emails a single-use sign-in link to a verified, unblocked account so the user can log in "
                    + "without a password. Requesting a new link invalidates any earlier one."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Magic link email queued for delivery",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Account not found, not verified or blocked",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Account not found", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "No account found for this email",
                                              "path": "/api/v1/auth/magic-link/send",
                                              "timestamp": "2026-11-08T01:25:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Account not verified", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Please verify your email before using a magic link.",
                                              "path": "/api/v1/auth/magic-link/send",
                                              "timestamp": "2026-11-08T01:25:00Z"
                                            }
                                            """)
                            }))
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> sentMagicLinkToken(
            @Valid @RequestBody SendMagicLinkTokenDto sendMagicLinkTokenDto,
            HttpServletRequest request) {

        UserResponseDto user = userService.sentMagicLinkToken(sendMagicLinkTokenDto);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "We've sent a magic sign-in link to your email. It works once and expires shortly, "
                        + "so please don't share it with anyone.",
                user,
                request.getRequestURI()));
    }

    @PostMapping("/magic-link/resend")
    @Operation(
            summary = "Resend the magic sign-in link",
            description = "Issues a fresh magic link after one was already requested. There is a 60-second "
                    + "cooldown between links, and the previous link stops working as soon as the new one is sent."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "New magic link email queued for delivery",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "No link requested yet, cooldown still running, or account not eligible",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Cooldown running", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Please wait 42 seconds before requesting another link.",
                                              "path": "/api/v1/auth/magic-link/resend",
                                              "timestamp": "2026-11-08T01:30:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "No link requested", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "No magic link has been requested yet. Please request one first.",
                                              "path": "/api/v1/auth/magic-link/resend",
                                              "timestamp": "2026-11-08T01:30:00Z"
                                            }
                                            """)
                            }))
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> resendMagicLinkToken(
            @Valid @RequestBody ResendMagicLinkTokenDto resendMagicLinkTokenDto,
            HttpServletRequest request) {

        UserResponseDto user = userService.resendMagicLinkToken(resendMagicLinkTokenDto);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "We've sent you a new magic sign-in link. Your previous link no longer works.",
                user,
                request.getRequestURI()));
    }

    @PostMapping("/magic-link/verify")
    @Operation(
            summary = "Sign in with a magic link token",
            description = "Exchanges the token from the emailed magic link for the user profile plus an access "
                    + "token and a refresh token. The link works once and expires after a short time. "
                    + "The account must be verified and not blocked."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Signed in, tokens issued",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Token invalid, expired or already used, or the account is blocked or unverified",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Invalid or expired link", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Invalid or expired magic link",
                                              "path": "/api/v1/auth/magic-link/verify",
                                              "timestamp": "2026-11-08T01:35:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Account blocked", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Your account has been blocked. Please contact support.",
                                              "path": "/api/v1/auth/magic-link/verify",
                                              "timestamp": "2026-11-08T01:35:00Z"
                                            }
                                            """)
                            }))
    })
    public ResponseEntity<APISuccessResponse<LoginResponseDto>> verifyMagicLinkToken(
            @Valid @RequestParam VerifyMagicLinkTokenDto verifyMagicLinkTokenDto,
            HttpServletRequest request) {

        LoginResponseDto loggedIn = userService.verifyMagicLinkToken(verifyMagicLinkTokenDto);

        String name = loggedIn.firstName() == null || loggedIn.firstName().isBlank()
                ? "" : ", " + loggedIn.firstName();

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Welcome back" + name + "! You have signed in with your magic link.",
                loggedIn,
                request.getRequestURI()));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','IT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List all users (admin only)",
            description = "Returns a page of users. Invalid paging or sorting values fall back to safe defaults: "
                    + "page 0, size 20 (max 100), sorted by createdAt descending. Results are cached for up to "
                    + "5 minutes, so the first request reads the database and repeats are served from the cache."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Page of users returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<PagedResponseDto<UserResponseDto>>> fetchAllUsers(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size (1-100, default 20)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Sort field",
                    schema = @Schema(allowableValues = {"firstName", "lastName", "email", "role", "createdAt", "updatedAt"},
                            defaultValue = "createdAt"))
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction",
                    schema = @Schema(allowableValues = {"ASC", "DESC"}, defaultValue = "DESC"))
            @RequestParam(required = false) String sortDirection,
            HttpServletRequest request) {

        PagedResponseDto<UserResponseDto> users =
                userService.fetchAllUsers(PageQuery.of(page, size, sortBy, sortDirection));

        String message = users.totalElements() == 0
                ? "No users found."
                : "Users retrieved successfully. Showing " + users.numberOfElements()
                  + " of " + users.totalElements() + " user(s).";

        return ResponseEntity.ok(APISuccessResponse.ok(message, users, request.getRequestURI()));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','IT_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Get one user by ID (admin only)",
            description = "Returns the profile of a single, non-deleted user. The result is cached for up to "
                    + "5 minutes."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User returned",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "User not found or the ID is not a valid UUID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = @ExampleObject(name = "User not found", value = """
                                    {
                                      "success": false,
                                      "status": 400,
                                      "error": "Bad Request",
                                      "errorCode": "BAD_REQUEST",
                                      "message": "User not found",
                                      "path": "/api/v1/auth/users/3f2b8c1e-9a4d-4e57-8b21-6c0d1f7a9e42",
                                      "timestamp": "2026-11-08T01:40:00Z"
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content)
    })
    public ResponseEntity<APISuccessResponse<UserResponseDto>> fetchSingleUser(
            @Parameter(description = "ID of the user to fetch", required = true)
            @PathVariable UUID userId,
            HttpServletRequest request) {

        UserResponseDto user = userService.fetchSingleUser(userId);

        return ResponseEntity.ok(APISuccessResponse.ok(
                "User retrieved successfully.",
                user,
                request.getRequestURI()));
    }

    @PostMapping("/google")
    @Operation(
            summary = "Sign in or sign up with Google",
            description = "Takes the Firebase ID token the client gets after Google sign-in, verifies it on the "
                    + "server, and returns the user profile with an access token and a refresh token. "
                    + "First-time Google users get a verified STUDENT account automatically. An existing account "
                    + "with the same email is linked to the Google identity, and a pending email verification "
                    + "is completed because Google has already verified the address."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Signed in with Google, tokens issued",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APISuccessResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Token invalid, expired or revoked, wrong sign-in provider, unverified Google "
                            + "email, or the account is blocked or deleted",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = APIErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "Invalid Firebase token", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Invalid or expired Firebase token",
                                              "path": "/api/v1/auth/google",
                                              "timestamp": "2026-11-08T01:45:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Account blocked", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "Your account has been blocked. Please contact support.",
                                              "path": "/api/v1/auth/google",
                                              "timestamp": "2026-11-08T01:45:00Z"
                                            }
                                            """),
                                    @ExampleObject(name = "Linked to another Google account", value = """
                                            {
                                              "success": false,
                                              "status": 400,
                                              "error": "Bad Request",
                                              "errorCode": "BAD_REQUEST",
                                              "message": "This email is linked to a different Google account",
                                              "path": "/api/v1/auth/google",
                                              "timestamp": "2026-11-08T01:45:00Z"
                                            }
                                            """)
                            }))
    })
    public ResponseEntity<APISuccessResponse<LoginResponseDto>> loginViaGoogle(
            @Valid @RequestBody FirebaseIdTokenDto firebaseIdTokenDto,
            HttpServletRequest request) {

        LoginResponseDto loggedIn = userService.loginViaGoogle(firebaseIdTokenDto);

        String name = loggedIn.firstName() == null || loggedIn.firstName().isBlank()
                ? "" : ", " + loggedIn.firstName();

        return ResponseEntity.ok(APISuccessResponse.ok(
                "Welcome" + name + "! You have signed in with Google.",
                loggedIn,
                request.getRequestURI()));
    }

}