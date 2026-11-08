package com.learningLadder.learningLadder.config.secuirtyConfig;
import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.jwtFilter.JWTFilter;
import com.learningLadder.learningLadder.config.ratingLimiting.RateLimitingFilter;
import com.learningLadder.learningLadder.enums.userRole.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;

import static com.learningLadder.learningLadder.enums.userRole.UserRole.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**"
    };

    private static final String AUTH_BASE = "/api/*/auth";

    private static final String[] PUBLIC_POST_ROUTES = {
            AUTH_BASE + "/register",
            AUTH_BASE + "/verify-otp",
            AUTH_BASE + "/login",
            AUTH_BASE + "/refresh-token",
            AUTH_BASE + "/resend-otp",
            AUTH_BASE + "/logout",
            AUTH_BASE + "/forgot-password",
            AUTH_BASE + "/reset-password",
            AUTH_BASE + "/magic-link/send",
            AUTH_BASE + "/magic-link/resend",
            AUTH_BASE + "/magic-link/verify",
            AUTH_BASE + "/google"
    };

    private static final String[] ADMIN_READ_ROUTES = {
            AUTH_BASE + "/users",
            AUTH_BASE + "/users/*"
    };

    private static final String COURSE_BASE = "/api/*/course";

    private static final String[] COURSE_READ_ROUTES = {
            COURSE_BASE,
            COURSE_BASE + "/search",
            COURSE_BASE + "/count",
            COURSE_BASE + "/*",
            COURSE_BASE + "/*/pdf"
    };

    private static final String[] COURSE_CREATE_ROUTES = { COURSE_BASE };
    private static final String[] COURSE_UPDATE_ROUTES = { COURSE_BASE + "/*" };
    private static final String[] COURSE_DELETE_ROUTES = { COURSE_BASE + "/*" };

    private static final String[] PLATFORM_ADMIN_ROLES = roles(SUPER_ADMIN, ADMIN, IT_ADMIN);

    private static final String[] COURSE_WRITE_ROLES = roles(
            SUPER_ADMIN, ADMIN,
            ACADEMIC_DIRECTOR,
            DEAN, DEPUTY_DEAN,
            HEAD_OF_DEPARTMENT, DEPUTY_HEAD_OF_DEPARTMENT,
            PROFESSOR, LECTURER);

    private static final String[] COURSE_DELETE_ROLES = roles(SUPER_ADMIN, ADMIN, ACADEMIC_DIRECTOR);

    private static final String STUDENT_PROFILE_BASE = "/api/*/student-profile";

    private static final String[] STUDENT_PROFILE_READ_ROUTES = {
            STUDENT_PROFILE_BASE,
            STUDENT_PROFILE_BASE + "/search",
            STUDENT_PROFILE_BASE + "/count",
            STUDENT_PROFILE_BASE + "/download",
            STUDENT_PROFILE_BASE + "/*"
    };

    private static final String[] STUDENT_PROFILE_CREATE_ROUTES = { STUDENT_PROFILE_BASE };
    private static final String[] STUDENT_PROFILE_UPDATE_ROUTES = { STUDENT_PROFILE_BASE + "/*" };
    private static final String[] STUDENT_PROFILE_DELETE_ROUTES = { STUDENT_PROFILE_BASE + "/*" };

    private static final String[] STUDENT_PROFILE_STAFF_ROLES = roles(
            SUPER_ADMIN, ADMIN, IT_ADMIN,
            REGISTRAR, DEPUTY_REGISTRAR,
            ADMISSIONS_OFFICER, STUDENT_REGISTRAR);

    private static final String[] STUDENT_PROFILE_MANAGE_ROLES = roles(
            STUDENT, SUPER_ADMIN, ADMIN, IT_ADMIN);

    private final JWTFilter jwtFilter;
    private final ObjectMapper objectMapper;
    private final RateLimitingFilter rateLimitingFilter;

    private static String[] roles(UserRole... roles) {
        return Arrays.stream(roles).map(Enum::name).toArray(String[]::new);
    }

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public FilterRegistrationBean<JWTFilter> jwtFilterRegistration(JWTFilter filter) {
        FilterRegistrationBean<JWTFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())

                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny))

                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                        .requestMatchers(EndpointRequest.toAnyEndpoint())
                        .hasAnyRole(PLATFORM_ADMIN_ROLES)

                        .requestMatchers(SWAGGER_WHITELIST).permitAll()

                        .requestMatchers(HttpMethod.GET, ADMIN_READ_ROUTES)
                        .hasAnyRole(PLATFORM_ADMIN_ROLES)

                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ROUTES).permitAll()

                        .requestMatchers(HttpMethod.GET, COURSE_READ_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.POST, COURSE_CREATE_ROUTES).hasAnyRole(COURSE_WRITE_ROLES)
                        .requestMatchers(HttpMethod.PATCH, COURSE_UPDATE_ROUTES).hasAnyRole(COURSE_WRITE_ROLES)
                        .requestMatchers(HttpMethod.DELETE, COURSE_DELETE_ROUTES).hasAnyRole(COURSE_DELETE_ROLES)

                        .requestMatchers(HttpMethod.GET, STUDENT_PROFILE_READ_ROUTES).hasAnyRole(STUDENT_PROFILE_STAFF_ROLES)
                        .requestMatchers(HttpMethod.POST, STUDENT_PROFILE_CREATE_ROUTES).hasRole(STUDENT.name())
                        .requestMatchers(HttpMethod.PATCH, STUDENT_PROFILE_UPDATE_ROUTES).hasAnyRole(STUDENT_PROFILE_MANAGE_ROLES)
                        .requestMatchers(HttpMethod.DELETE, STUDENT_PROFILE_DELETE_ROUTES).hasAnyRole(STUDENT_PROFILE_MANAGE_ROLES)

                        .anyRequest().authenticated())

                .addFilterAfter(rateLimitingFilter, CorsFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> writeError(request, response,
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required to access this resource");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> writeError(request, response,
                HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You do not have permission to access this resource");
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String errorCode, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                APIErrorResponse.of(status, errorCode, message, request.getRequestURI()));
    }
}