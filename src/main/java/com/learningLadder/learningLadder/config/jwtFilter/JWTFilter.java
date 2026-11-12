package com.learningLadder.learningLadder.config.jwtFilter;

import com.learningLadder.learningLadder.config.jwtConfig.JWTConfig;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTR = "jwt.auth.error";

    public record AuthError(String code, String message) { }

    private static final String BEARER_PREFIX = "Bearer ";

    private final JWTConfig jwtConfig;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = authHeader.substring(BEARER_PREFIX.length()).trim();

            try {
                String username = jwtConfig.extractUsernameFromAccessToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtConfig.isAccessTokenValid(token, userDetails)
                        && userDetails.isEnabled()
                        && userDetails.isAccountNonLocked()) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (ExpiredJwtException ex) {
                reject(request, "TOKEN_EXPIRED", "Access token has expired");
            } catch (JwtException | IllegalArgumentException ex) {
                reject(request, "TOKEN_INVALID", "Invalid authentication token");
            } catch (Exception ex) {
                reject(request, "AUTHENTICATION_FAILED", "Authentication failed");
            }
        }

        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, String code, String message) {
        SecurityContextHolder.clearContext();
        request.setAttribute(AUTH_ERROR_ATTR, new AuthError(code, message));
    }
}