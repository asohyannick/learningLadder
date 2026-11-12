package com.learningLadder.learningLadder.service.userService;

import com.learningLadder.learningLadder.config.backgroundJob.FireAndForgetEmailAsync.FireAndForgetEmailAsync;
import com.learningLadder.learningLadder.config.cachingConfig.CacheNames.CacheNames;
import com.learningLadder.learningLadder.config.firebaseAdminConfig.FirebaseTokenVerifier;
import com.learningLadder.learningLadder.config.jwtConfig.JWTConfig;
import com.learningLadder.learningLadder.config.resendConfig.resendEmailTemplateAsync.ResendEmailTemplateAsync;
import com.learningLadder.learningLadder.dto.userDto.*;
import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.accountStatus.AccountStatus;
import com.learningLadder.learningLadder.enums.userRole.UserRole;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.exception.conflictRequestException.ConflictRequestException;
import com.learningLadder.learningLadder.exception.forbiddenRequestException.ForbiddenRequestException;
import com.learningLadder.learningLadder.mappers.userMapper.UserMapper;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import com.learningLadder.learningLadder.service.userDetails.CustomUserDetailsService;
import com.learningLadder.learningLadder.utils.otpHasher.OtpHasher;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {
    private static final long OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final String TOKEN_TYPE = "Bearer";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final ResendEmailTemplateAsync emailTemplateAsync;
    private final PasswordEncoder passwordEncoder;
    private final JWTConfig jwtConfig;
    private final FireAndForgetEmailAsync fireAndForgetEmailAsync;
    private final UserMapper userMapper;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final CustomUserDetailsService customUserDetailsService;
    private final OtpHasher otpHasher;

    @Value("${app.security.otp-expiry-minutes:10}")
    private int otpExpiryMinutes;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${app.security.reset-password-expiry-minutes:15}")
    private int resetPasswordExpiryMinutes;

    @Value("${app.security.magic-link-expiry-minutes:15}")
    private int magicLinkExpiryMinutes;

    public UserResponseDto register(RegisterRequestDto registerRequestDto) {
        String email = normalizeEmail(registerRequestDto.email());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = userMapper.toEntity(registerRequestDto);
        user.setPassword(passwordEncoder.encode(registerRequestDto.password()));
        user.setRole(UserRole.STUDENT);
        user.setStatus(AccountStatus.UNVERIFIED);
        user.setAccountVerified(false);

        String otp = assignNewOtp(user);

        User saved = saveNewUser(user);

        final String to = saved.getEmail();
        final String firstName = saved.getFirstName();
        final int expiry = otpExpiryMinutes;
        fireAndForgetEmailAsync.fire(
                "registration OTP for user " + saved.getId(),
                () -> emailTemplateAsync.sendRegistrationOtpEmail(to, firstName, otp, expiry));

        return toUserResponse(saved);
    }

    public UserResponseDto verifyOtpCode(VerifyOtpCodeDto verifyOtpCodeDto) {
        String code = otpHasher.normalize(verifyOtpCodeDto.otpCode());
        if (code.length() != OtpHasher.CODE_LENGTH) {
            throw new BadRequestException("Invalid or expired verification code");
        }

        User user = userRepository.findByOtpCode(otpHasher.hash(code))
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException("Invalid or expired verification code"));

        if (user.isAccountBlocked() || user.getStatus() == AccountStatus.BLOCKED) {
            throw new BadRequestException("This account has been blocked. Please contact support.");
        }
        if (user.getStatus() == AccountStatus.VERIFIED || user.isAccountVerified()) {
            throw new BadRequestException("This account is already verified");
        }
        if (user.getOptCodeExpirationDate() == null || user.getOptCodeExpirationDate().isBefore(Instant.now())) {
            throw new BadRequestException("Verification code has expired. Please request a new one.");
        }

        user.setStatus(AccountStatus.VERIFIED);
        user.setAccountVerified(true);
        user.setOptCodeVerified(true);
        user.setOtpCode(null);
        user.setOptCodeExpirationDate(null);
        User saved = userRepository.save(user);

        final String to = saved.getEmail();
        final String firstName = saved.getFirstName();
        final String loginUrl = frontendUrl + "/login";
        fireAndForgetEmailAsync.fire(
                "account activated for user " + saved.getId(),
                () -> emailTemplateAsync.sendAccountActivatedEmail(to, firstName, loginUrl));

        return toUserResponse(saved);
    }

    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        String invalid = "Invalid email or password";

        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(loginRequestDto.email()))
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException(invalid));

        if (user.getPassword() == null
                || !passwordEncoder.matches(loginRequestDto.password(), user.getPassword())) {
            throw new BadRequestException(invalid);
        }
        if (user.isAccountBlocked() || user.getStatus() == AccountStatus.BLOCKED) {
            throw new BadRequestException("Your account has been blocked. Please contact support.");
        }
        if (user.getStatus() != AccountStatus.VERIFIED) {
            throw new BadRequestException("Please verify your email with the OTP we sent you before logging in.");
        }

        IssuedTokens tokens = issueTokens(user);
        user.setFailedLoginAttempts(0);
        User saved = userRepository.save(user);

        return new LoginResponseDto(
                saved.getId(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getEmail(),
                saved.getRole(),
                saved.isAccountVerified(),
                saved.isAccountDeleted(),
                saved.isAccountBlocked(),
                tokens.accessToken(),
                lifetimeSeconds(tokens.accessClaims()),
                tokens.accessClaims().getExpiration().toInstant(),
                tokens.refreshToken(),
                lifetimeSeconds(tokens.refreshClaims()),
                tokens.refreshClaims().getExpiration().toInstant(),
                TOKEN_TYPE,
                saved.getCreatedAt(),
                saved.getUpdatedAt());
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public GenerateNewAccessTokenResponseDto generateNewAccessToken(GenerateNewAccessToken request) {
        String invalid = "Invalid or expired refresh token";
        String presented = request.refreshToken();

        Claims claims;
        try {
            claims = jwtConfig.parseRefreshToken(presented);
        } catch (JwtException | BadRequestException ex) {
            throw new BadRequestException(invalid);
        }

        User user = userRepository.findByEmailIgnoreCase(claims.getSubject())
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException(invalid));

        if (user.getRefreshToken() == null) {
            throw new BadRequestException(invalid);
        }

        boolean matchesStored = MessageDigest.isEqual(
                user.getRefreshToken().getBytes(StandardCharsets.UTF_8),
                sha256(presented).getBytes(StandardCharsets.UTF_8));

        if (!matchesStored) {
            user.setRefreshToken(null);
            userRepository.save(user);
            throw new BadRequestException(invalid);
        }

        if (user.isAccountBlocked() || user.getStatus() != AccountStatus.VERIFIED) {
            user.setRefreshToken(null);
            userRepository.save(user);
            throw new BadRequestException("This account cannot refresh its session");
        }

        IssuedTokens tokens = issueTokens(user);
        userRepository.save(user);

        return new GenerateNewAccessTokenResponseDto(
                tokens.accessToken(),
                lifetimeSeconds(tokens.accessClaims()),
                tokens.accessClaims().getExpiration().toInstant(),
                tokens.refreshToken(),
                lifetimeSeconds(tokens.refreshClaims()),
                tokens.refreshClaims().getExpiration().toInstant(),
                TOKEN_TYPE
        );
    }

    public UserResponseDto resendOtpCode(ResendOtpCodeDto resendOtpCodeDto) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(resendOtpCodeDto.email()))
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException("No account found for this email"));

        if (user.isAccountBlocked() || user.getStatus() == AccountStatus.BLOCKED) {
            throw new BadRequestException("This account has been blocked. Please contact support.");
        }
        if (user.getStatus() == AccountStatus.VERIFIED || user.isAccountVerified()) {
            throw new BadRequestException("This account is already verified");
        }

        Instant now = Instant.now();
        if (user.getOtpCode() != null && user.getOptCodeExpirationDate() != null) {
            Instant issuedAt = user.getOptCodeExpirationDate().minus(otpExpiryMinutes, ChronoUnit.MINUTES);
            long secondsSinceIssued = Duration.between(issuedAt, now).getSeconds();
            if (secondsSinceIssued < OTP_RESEND_COOLDOWN_SECONDS) {
                long wait = OTP_RESEND_COOLDOWN_SECONDS - secondsSinceIssued;
                throw new BadRequestException("A code was just sent. Please wait " + wait
                        + (wait == 1 ? " second" : " seconds") + " before requesting a new one.");
            }
        }

        String otp = assignNewOtp(user);
        User saved = userRepository.save(user);

        final String to = saved.getEmail();
        final String firstName = saved.getFirstName();
        final int expiry = otpExpiryMinutes;
        fireAndForgetEmailAsync.fire(
                "resend OTP for user " + saved.getId(),
                () -> emailTemplateAsync.sendResendOtpEmail(to, firstName, otp, expiry));

        return toUserResponse(saved);
    }

    public UserResponseDto logout(LogoutRequestDto logoutRequestDto) {
        String presented = logoutRequestDto.refreshToken();

        Claims claims;
        try {
            claims = jwtConfig.parseRefreshToken(presented);
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            throw new BadRequestException("Refresh token has expired");
        } catch (JwtException | BadRequestException ex) {
            throw new BadRequestException("Invalid refresh token");
        }

        User user = userRepository.findByEmailIgnoreCase(claims.getSubject())
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        if (user.getRefreshToken() == null
                || !MessageDigest.isEqual(
                user.getRefreshToken().getBytes(StandardCharsets.UTF_8),
                sha256(presented).getBytes(StandardCharsets.UTF_8))) {
            throw new BadRequestException("Invalid refresh token");
        }

        user.setRefreshToken(null);
        user.setAccessToken(null);
        return toUserResponse(userRepository.save(user));
    }

    public UserResponseDto forgotPassword(ForgotPasswordDto forgotPasswordDto) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(forgotPasswordDto.email()))
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException("No account found for this email"));

        if (user.isAccountBlocked() || user.getStatus() == AccountStatus.BLOCKED) {
            throw new BadRequestException("This account has been blocked. Please contact support.");
        }
        if (user.getStatus() != AccountStatus.VERIFIED) {
            throw new BadRequestException("Please verify your email before resetting your password.");
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        user.setForgotPassword(sha256(token));
        user.setForgotPasswordExpirationDate(Instant.now().plus(resetPasswordExpiryMinutes, ChronoUnit.MINUTES));
        User saved = userRepository.save(user);

        final String to = saved.getEmail();
        final String firstName = saved.getFirstName();
        final String resetLink = frontendUrl + "/reset-password?token=" + token;
        final int expiry = resetPasswordExpiryMinutes;
        fireAndForgetEmailAsync.fire(
                "forgot password for user " + saved.getId(),
                () -> emailTemplateAsync.sendForgotPasswordEmail(to, firstName, resetLink, expiry));

        return toUserResponse(saved);
    }

    public UserResponseDto resetPassword(TokenQueryDto tokenQuery,  ResetPasswordDto resetPasswordDto) {
        String invalid = "Invalid or expired reset link";

        if (!resetPasswordDto.newPassword().equals(resetPasswordDto.confirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        User user = userRepository.findByForgotPassword(sha256(tokenQuery.token()))
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException(invalid));

        if (user.getForgotPasswordExpirationDate() == null
                || user.getForgotPasswordExpirationDate().isBefore(Instant.now())) {
            throw new BadRequestException(invalid);
        }
        if (user.isAccountBlocked() || user.getStatus() == AccountStatus.BLOCKED) {
            throw new BadRequestException("This account has been blocked. Please contact support.");
        }
        if (user.getPassword() != null
                && passwordEncoder.matches(resetPasswordDto.newPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from your current password");
        }

        user.setPassword(passwordEncoder.encode(resetPasswordDto.newPassword()));
        user.setForgotPassword(null);
        user.setForgotPasswordExpirationDate(null);
        user.setFailedLoginAttempts(0);
        user.setRefreshToken(null);
        user.setAccessToken(null);
        User saved = userRepository.save(user);

        final String to = saved.getEmail();
        final String firstName = saved.getFirstName();
        final String loginUrl = frontendUrl + "/login";
        fireAndForgetEmailAsync.fire(
                "password reset success for user " + saved.getId(),
                () -> emailTemplateAsync.sendPasswordResetSuccessEmail(to, firstName, loginUrl));

        return toUserResponse(saved);
    }

    public UserResponseDto blockAccount(UUID userId, BlockAccountRequestDto accountRequestDto) {
        User admin = currentUser();
        User target = findActiveUserById(userId);

        if (target.getId().equals(admin.getId())) {
            throw new BadRequestException("You cannot block your own account");
        }
        if (target.getRole() == UserRole.SUPER_ADMIN) {
            throw new BadRequestException("A super admin account cannot be blocked");
        }
        if (target.isAccountBlocked() || target.getStatus() == AccountStatus.BLOCKED) {
            throw new BadRequestException("This account is already blocked");
        }

        target.setStatus(AccountStatus.BLOCKED);
        target.setAccountBlocked(true);
        target.setAccountUnBlocked(false);
        target.setRefreshToken(null);
        target.setAccessToken(null);
        User saved = userRepository.save(target);

        final String to = saved.getEmail();
        final String firstName = saved.getFirstName();
        final String reason = accountRequestDto.reason().trim();
        fireAndForgetEmailAsync.fire(
                "account blocked for user " + saved.getId(),
                () -> emailTemplateAsync.sendAccountBlockedEmail(to, firstName, reason));

        return toUserResponse(saved);
    }

    public UserResponseDto unBlockAccount(UUID userId, UnBlockAccountRequestDto unBlockAccountRequestDto) {
        User target = findActiveUserById(userId);

        if (!target.isAccountBlocked() && target.getStatus() != AccountStatus.BLOCKED) {
            throw new BadRequestException("This account is not blocked");
        }

        target.setStatus(target.isAccountVerified() ? AccountStatus.VERIFIED : AccountStatus.UNVERIFIED);
        target.setAccountBlocked(false);
        target.setAccountUnBlocked(true);
        User saved = userRepository.save(target);

        final String to = saved.getEmail();
        final String firstName = saved.getFirstName();
        final String loginUrl = frontendUrl + "/login";
        fireAndForgetEmailAsync.fire(
                "account unblocked for user " + saved.getId(),
                () -> emailTemplateAsync.sendAccountUnblockedEmail(to, firstName, loginUrl));

        return toUserResponse(saved);
    }

    public UserResponseDto deleteAccount(UUID userId, DeleteAccountRequestDto deleteAccountRequestDto) {
        User current = currentUser();

        if(!String.valueOf(current.getId()).equalsIgnoreCase(String.valueOf(userId))) {
            log.warn("Account deletion refused: token belongs to {} ({}), but userId {} was requested",
                    current.getId(), current.getEmail(), userId);
            throw new ForbiddenRequestException("You can only delete your own account");
        }
        if (current.getPassword() == null
                || !passwordEncoder.matches(deleteAccountRequestDto.password(), current.getPassword())) {
            throw new BadRequestException("Incorrect password");
        }
        if (current.getRole() == UserRole.SUPER_ADMIN) {
            throw new BadRequestException("A super admin account cannot be deleted");
        }

        current.setAccountDeleted(true);
        current.setRefreshToken(null);
        current.setAccessToken(null);
        current.setForgotPassword(null);
        current.setForgotPasswordExpirationDate(null);
        current.setOtpCode(null);
        current.setOptCodeExpirationDate(null);
        current.setMagicLinkToken(null);
        current.setMagicLinkTokenExpirationDate(null);

        return toUserResponse(userRepository.save(current));
    }

    public UserResponseDto sentMagicLinkToken(SendMagicLinkTokenDto sendMagicLinkTokenDto) {
        User user = findMagicLinkEligibleUser(sendMagicLinkTokenDto.email());
        return issueMagicLink(user);
    }

    public UserResponseDto resendMagicLinkToken(ResendMagicLinkTokenDto resendMagicLinkTokenDto) {
        User user = findMagicLinkEligibleUser(resendMagicLinkTokenDto.email());

        if (user.getMagicLinkToken() == null || user.getMagicLinkTokenExpirationDate() == null) {
            throw new BadRequestException("No magic link has been requested yet. Please request one first.");
        }

        Instant now = Instant.now();
        Instant issuedAt = user.getMagicLinkTokenExpirationDate().minus(magicLinkExpiryMinutes, ChronoUnit.MINUTES);
        Instant nextAllowed = issuedAt.plusSeconds(60);
        if (now.isBefore(nextAllowed)) {
            long wait = java.time.Duration.between(now, nextAllowed).getSeconds() + 1;
            throw new BadRequestException("Please wait " + wait + " seconds before requesting another link.");
        }

        return issueMagicLink(user);
    }

    public LoginResponseDto verifyMagicLinkToken(VerifyMagicLinkTokenDto verifyMagicLinkTokenDto) {
        String invalid = "Invalid or expired magic link";

        User user = userRepository.findByMagicLinkToken(sha256(verifyMagicLinkTokenDto.magicLinkToken().trim()))
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException(invalid));

        if (user.getMagicLinkTokenExpirationDate() == null
                || user.getMagicLinkTokenExpirationDate().isBefore(Instant.now())) {
            throw new BadRequestException(invalid);
        }
        if (user.isAccountBlocked() || user.getStatus() == AccountStatus.BLOCKED) {
            throw new BadRequestException("Your account has been blocked. Please contact support.");
        }
        if (user.getStatus() != AccountStatus.VERIFIED) {
            throw new BadRequestException("Please verify your email before signing in.");
        }

        user.setMagicLinkToken(null);
        user.setMagicLinkTokenExpirationDate(null);
        user.setFailedLoginAttempts(0);

        IssuedTokens tokens = issueTokens(user);
        User saved = userRepository.save(user);
        return toLoginResponse(saved, tokens);
    }

    @Cacheable(cacheNames = CacheNames.USERS, key = "'all:' + #pageQuery.cacheKey()")
    @Transactional(readOnly = true)
    public PagedResponseDto<UserResponseDto> fetchAllUsers(PageQuery pageQuery) {
        Page<User> page = userRepository.findAll(pageQuery.toPageable());

        List<UserResponseDto> content = new ArrayList<>();
        for (User user : page.getContent()) {
            content.add(toUserResponse(user));
        }

        return new PagedResponseDto<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getNumberOfElements(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious());
    }

    @Cacheable(cacheNames = CacheNames.USERS, key = "'id:' + #userId")
    @Transactional(readOnly = true)
    public UserResponseDto fetchSingleUser(UUID userId) {
        return toUserResponse(findActiveUserById(userId));
    }

    @CacheEvict(cacheNames = CacheNames.USERS, allEntries = true)
    public LoginResponseDto loginViaGoogle(FirebaseIdTokenDto firebaseIdTokenDto) {
        FirebaseTokenVerifier.FirebaseUserInfo info = firebaseTokenVerifier.verify(firebaseIdTokenDto.idToken());

        if (!"google.com".equals(info.signInProvider())) {
            throw new BadRequestException("This endpoint only accepts Google sign-in");
        }
        if (info.email() == null || info.email().isBlank() || !info.emailVerified()) {
            throw new BadRequestException("Your Google account must have a verified email address");
        }
        String email = normalizeEmail(info.email());

        User user = userRepository.findByFirebaseUid(info.uid())
                .or(() -> userRepository.findByEmailIgnoreCase(email))
                .orElse(null);

        if (user == null) {
            user = createGoogleUser(info, email);
        } else {
            if (user.isAccountDeleted()) {
                throw new BadRequestException("This account no longer exists");
            }
            if (user.isAccountBlocked() || user.getStatus() == AccountStatus.BLOCKED) {
                throw new BadRequestException("Your account has been blocked. Please contact support.");
            }
            if (user.getFirebaseUid() != null && !user.getFirebaseUid().equals(info.uid())) {
                throw new BadRequestException("This email is linked to a different Google account");
            }

            if (user.getFirebaseUid() == null) {
                user.setFirebaseUid(info.uid());
            }
            if (user.getStatus() != AccountStatus.VERIFIED) {
                user.setStatus(AccountStatus.VERIFIED);
                user.setAccountVerified(true);
                user.setOptCodeVerified(true);
                user.setOtpCode(null);
                user.setOptCodeExpirationDate(null);
            }
        }

        user.setFailedLoginAttempts(0);
        IssuedTokens tokens = issueTokens(user);
        User saved = userRepository.save(user);
        return toLoginResponse(saved, tokens);
    }

    private User saveNewUser(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("An account with this email already exists");
        }
    }

    private record IssuedTokens(String accessToken, Claims accessClaims,
                                String refreshToken, Claims refreshClaims) { }

    private IssuedTokens issueTokens(User user) {
        UserDetails details = customUserDetailsService.loadUserByUsername(user.getEmail());
        String access = jwtConfig.generateAccessToken(details);
        String refresh = jwtConfig.generateRefreshToken(details);
        user.setRefreshToken(sha256(refresh));
        return new IssuedTokens(access, jwtConfig.parseAccessToken(access),
                refresh, jwtConfig.parseRefreshToken(refresh));
    }

    private long lifetimeSeconds(Claims claims) {
        return (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
    }

    private UserResponseDto toUserResponse(User u) {
        return new UserResponseDto(
                u.getId(),
                u.getFirstName(),
                u.getLastName(),
                u.getEmail(),
                u.getRole(),
                u.isAccountVerified(),
                u.isAccountDeleted(),
                u.isAccountBlocked(),
                u.getCreatedAt(),
                u.getUpdatedAt());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new BadRequestException("SHA-256 not available", ex);
        }
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth.getName() == null || "anonymousUser".equals(auth.getName())) {
            throw new BadRequestException("You must be logged in to perform this action");
        }
        return userRepository.findByEmailIgnoreCase(auth.getName())
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException("Authenticated user not found"));
    }

    private User findActiveUserById(UUID userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException("User not found"));
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private User findMagicLinkEligibleUser(String email) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(u -> !u.isAccountDeleted())
                .orElseThrow(() -> new BadRequestException("No account found for this email"));

        if (user.isAccountBlocked() || user.getStatus() == AccountStatus.BLOCKED) {
            throw new BadRequestException("This account has been blocked. Please contact support.");
        }
        if (user.getStatus() != AccountStatus.VERIFIED) {
            throw new BadRequestException("Please verify your email before using a magic link.");
        }
        return user;
    }

    private UserResponseDto issueMagicLink(User user) {
        String token = generateSecureToken();
        user.setMagicLinkToken(sha256(token));
        user.setMagicLinkTokenExpirationDate(Instant.now().plus(magicLinkExpiryMinutes, ChronoUnit.MINUTES));
        User saved = userRepository.save(user);

        final String to = saved.getEmail();
        final String firstName = saved.getFirstName();
        final String magicLink = frontendUrl + "/magic-link?token=" + token;
        final int expiry = magicLinkExpiryMinutes;
        fireAndForgetEmailAsync.fire(
                "magic link for user " + saved.getId(),
                () -> emailTemplateAsync.sendMagicLinkEmail(to, firstName, magicLink, expiry));

        return toUserResponse(saved);
    }

    private LoginResponseDto toLoginResponse(User saved, IssuedTokens tokens) {
        return new LoginResponseDto(
                saved.getId(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getEmail(),
                saved.getRole(),
                saved.isAccountVerified(),
                saved.isAccountDeleted(),
                saved.isAccountBlocked(),
                tokens.accessToken(),
                lifetimeSeconds(tokens.accessClaims()),
                tokens.accessClaims().getExpiration().toInstant(),
                tokens.refreshToken(),
                lifetimeSeconds(tokens.refreshClaims()),
                tokens.refreshClaims().getExpiration().toInstant(),
                TOKEN_TYPE,
                saved.getCreatedAt(),
                saved.getUpdatedAt());
    }

    private User createGoogleUser(FirebaseTokenVerifier.FirebaseUserInfo info, String email) {
        String[] names = splitName(info.name(), email);

        User user = new User();
        user.setFirstName(names[0]);
        user.setLastName(names[1]);
        user.setEmail(email);
        user.setPassword(null);
        user.setRole(UserRole.STUDENT);
        user.setStatus(AccountStatus.VERIFIED);
        user.setAccountVerified(true);
        user.setOptCodeVerified(true);
        user.setFirebaseUid(info.uid());

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("Could not complete Google sign-in. Please try again.");
        }
    }

    private String[] splitName(String fullName, String email) {
        String name = (fullName == null || fullName.isBlank())
                ? email.substring(0, email.indexOf('@') > 0 ? email.indexOf('@') : email.length())
                : fullName.trim();
        int space = name.indexOf(' ');
        if (space < 0) {
            return new String[]{name, null};
        }
        return new String[]{name.substring(0, space).trim(), name.substring(space + 1).trim()};
    }

    private String assignNewOtp(User user) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = otpHasher.generateCode();
            String hash = otpHasher.hash(code);
            if (!userRepository.existsByOtpCode(hash)) {
                user.setOtpCode(hash);
                user.setOptCodeExpirationDate(Instant.now().plus(Duration.ofMinutes(10)));
                user.setOptCodeVerified(false);
                return code;
            }
        }
        throw new ConflictRequestException("Could not generate a verification code. Please try again.");
    }
}