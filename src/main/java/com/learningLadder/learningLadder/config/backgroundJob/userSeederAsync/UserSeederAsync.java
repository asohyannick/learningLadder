package com.learningLadder.learningLadder.config.backgroundJob.userSeederAsync;

import com.learningLadder.learningLadder.entity.user.User;
import com.learningLadder.learningLadder.enums.accountStatus.AccountStatus;
import com.learningLadder.learningLadder.enums.userRole.UserRole;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import com.learningLadder.learningLadder.repository.userRepository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.bootstrap",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class UserSeederAsync {

    private static final int RECOMMENDED_MIN_PASSWORD_LENGTH = 12;

    private final BootstrapUserProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void seedUsers() {
        int created = 0;
        int skipped = 0;
        int failed = 0;

        for (Map.Entry<String, BootstrapUserProperties.Credentials> entry : properties.getUsers().entrySet()) {
            String roleKey = entry.getKey();
            BootstrapUserProperties.Credentials credentials = entry.getValue();

            try {
                UserRole role = toRole(roleKey);
                if (role == null) {
                    log.error("Seeder: no UserRole matches '{}', skipping", roleKey);
                    failed++;
                    continue;
                }

                String email = credentials.getEmail() == null ? "" : credentials.getEmail().trim().toLowerCase(Locale.ROOT);
                String rawPassword = credentials.getPassword();

                if (email.isBlank() || rawPassword == null || rawPassword.isBlank()) {
                    log.warn("Seeder: email or password is empty for '{}', skipping", roleKey);
                    failed++;
                    continue;
                }

                if (userRepository.existsByEmailIgnoreCase(email)) {
                    skipped++;
                    continue;
                }

                if (rawPassword.length() < RECOMMENDED_MIN_PASSWORD_LENGTH) {
                    log.warn("Seeder: password for '{}' is shorter than {} characters, use a stronger one",
                            roleKey, RECOMMENDED_MIN_PASSWORD_LENGTH);
                }

                userRepository.save(buildUser(email, passwordEncoder.encode(rawPassword), role));
                created++;
                log.info("Seeder: created {} account ({})", role, maskEmail(email));

            } catch (DataIntegrityViolationException ex) {
                skipped++;
            } catch (Exception ex) {
                failed++;
                log.error("Seeder: failed to seed '{}': {}", roleKey, ex.getMessage());
            }
        }

        log.info("User seeding finished: {} created, {} already existed, {} failed", created, skipped, failed);
    }

    private User buildUser(String email, String hashedPassword, UserRole role) {
        return User.builder()
                .firstName(toDisplayName(role))
                .lastName("Account")
                .email(email)
                .password(hashedPassword)
                .role(role)
                .status(AccountStatus.VERIFIED)
                .isAccountVerified(true)
                .optCodeVerified(true)
                .isAccountBlocked(false)
                .isAccountUnBlocked(false)
                .isAccountDeleted(false)
                .failedLoginAttempts(0)
                .build();
    }

    private UserRole toRole(String key) {
        try {
            return UserRole.valueOf(key.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (BadRequestException ex) {
            return null;
        }
    }

    private String toDisplayName(UserRole role) {
        StringBuilder sb = new StringBuilder();
        for (String word : role.name().split("_")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}