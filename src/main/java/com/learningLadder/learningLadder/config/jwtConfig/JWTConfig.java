package com.learningLadder.learningLadder.config.jwtConfig;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JWTConfig {

    public static final String TOKEN_TYPE_CLAIM = "type";
    public static final String ACCESS = "access";
    public static final String REFRESH = "refresh";
    public static final String ROLES_CLAIM = "roles";

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JWTConfig(
            @Value("${app.security.jwt.access-token}") String accessSecret,
            @Value("${app.security.jwt.refresh-token}") String refreshSecret,
            @Value("${app.security.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${app.security.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        this.accessKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessSecret));
        this.refreshKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(refreshSecret));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(UserDetails user) {
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return buildToken(user.getUsername(), ACCESS, accessTokenExpirationMs, accessKey,
                java.util.Map.of(ROLES_CLAIM, roles));
    }

    public String generateRefreshToken(UserDetails user) {
        return buildToken(user.getUsername(),
                REFRESH,
                refreshTokenExpirationMs,
                refreshKey,
                java.util.Map.of()
        );
    }

    private String buildToken(String subject, String type, long expirationMs, SecretKey key,
                              Map<String, Object> extraClaims) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .claims(extraClaims)
                .claim(TOKEN_TYPE_CLAIM, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // ---------- Validation ----------

    /** Throws ExpiredJwtException, SignatureException, MalformedJwtException, etc. if invalid. */
    public Claims parseAccessToken(String token) {
        Claims claims = parse(token, accessKey);
        requireType(claims, ACCESS);
        return claims;
    }

    public Claims parseRefreshToken(String token) {
        Claims claims = parse(token, refreshKey);
        requireType(claims, REFRESH);
        return claims;
    }

    public boolean isAccessTokenValid(String token, UserDetails user) {
        Claims claims = parseAccessToken(token);
        return claims.getSubject().equals(user.getUsername())
                && claims.getExpiration().after(new Date());
    }

    public boolean isRefreshTokenValid(String token, UserDetails user) {
        Claims claims = parseRefreshToken(token);
        return claims.getSubject().equals(user.getUsername())
                && claims.getExpiration().after(new Date());
    }

    // ---------- Extraction helpers ----------

    public String extractUsernameFromAccessToken(String token) {
        return extractFromAccess(token, Claims::getSubject);
    }

    public String extractUsernameFromRefreshToken(String token) {
        return parseRefreshToken(token).getSubject();
    }

    public <T> T extractFromAccess(String token, Function<Claims, T> resolver) {
        return resolver.apply(parseAccessToken(token));
    }

    // ---------- Internals ----------

    private Claims parse(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void requireType(Claims claims, String expected) {
        if (!expected.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
            throw new JwtException("Invalid token type, expected " + expected);
        }
    }
}