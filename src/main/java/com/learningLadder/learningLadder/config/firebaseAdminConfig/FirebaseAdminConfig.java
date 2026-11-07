package com.learningLadder.learningLadder.config.firebaseAdminConfig;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.List;

@Slf4j
@Configuration
@Getter
@Setter
@ConfigurationProperties(prefix = "app.firebase")
@ConditionalOnProperty(
        prefix = "app.firebase",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class FirebaseAdminConfig {

    private boolean enabled = true;
    private String projectId;
    private String privateKeyId;
    private String privateKey;
    private String clientEmail;
    private String clientId;

    @Bean
    public FirebaseApp firebaseApp() {
        for (FirebaseApp existing : FirebaseApp.getApps()) {
            if (FirebaseApp.DEFAULT_APP_NAME.equals(existing.getName())) {
                return existing;
            }
        }

        requireText(projectId, "app.firebase.project-id");
        requireText(clientEmail, "app.firebase.client-email");
        requireText(privateKey, "app.firebase.private-key");

        try {
            ServiceAccountCredentials credentials = ServiceAccountCredentials.fromPkcs8(
                    clientId, clientEmail, normalizePrivateKey(privateKey), privateKeyId, List.of());

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setProjectId(projectId)
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            log.info("Firebase Admin initialised [project={}]", projectId);
            return app;
        } catch (IOException | BadRequestException ex) {
            throw new BadRequestException("Invalid Firebase service account credentials: " + ex.getMessage());
        }
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    private String normalizePrivateKey(String raw) {
        String key = raw.trim();
        if (key.length() > 1 && ((key.startsWith("\"") && key.endsWith("\""))
                || (key.startsWith("'") && key.endsWith("'")))) {
            key = key.substring(1, key.length() - 1);
        }
        return key.replace("\\n", "\n");
    }

    private void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(property + " is missing. Check your FIREBASE_* environment variables.");
        }
    }
}