package com.learningLadder.learningLadder.config.firebaseAdminConfig;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FirebaseTokenVerifier {

    private final FirebaseAuth firebaseAuth;

    public record FirebaseUserInfo(String uid,
                                   String email,
                                   boolean emailVerified,
                                   String name,
                                   String picture,
                                   String signInProvider) { }

    public FirebaseUserInfo verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BadRequestException("Firebase ID token is required");
        }
        try {
            FirebaseToken token = firebaseAuth.verifyIdToken(idToken, true);   // true = reject revoked tokens
            return new FirebaseUserInfo(
                    token.getUid(),
                    token.getEmail(),
                    token.isEmailVerified(),
                    token.getName(),
                    token.getPicture(),
                    extractProvider(token));
        } catch (FirebaseAuthException | BadRequestException ex) {
            log.warn("Firebase token rejected: {}", ex.getMessage());
            throw new BadRequestException("Invalid or expired Firebase token");
        }
    }

    private String extractProvider(FirebaseToken token) {
        Object firebaseClaim = token.getClaims().get("firebase");
        if (firebaseClaim instanceof Map<?, ?> map && map.get("sign_in_provider") instanceof String provider) {
            return provider;
        }
        return null;
    }
}