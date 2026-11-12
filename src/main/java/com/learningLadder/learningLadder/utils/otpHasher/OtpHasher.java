package com.learningLadder.learningLadder.utils.otpHasher;

import com.learningLadder.learningLadder.exception.badRequestException.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class OtpHasher {

    public static final int CODE_LENGTH = 6;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public OtpHasher(@Value("${app.security.otp-hash-secret}") String secret) {
        if (secret == null || secret.length() < 32) {
            throw new BadRequestException("app.security.otp-hash-secret must be at least 32 characters");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String generateCode() {
        int bound = (int) Math.pow(10, CODE_LENGTH);
        return String.format("%0" + CODE_LENGTH + "d", random.nextInt(bound));
    }

    public String normalize(String code) {
        return code == null ? "" : code.replaceAll("\\D", "");
    }

    public String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalize(code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new BadRequestException("Could not hash the OTP code", ex);
        }
    }
}