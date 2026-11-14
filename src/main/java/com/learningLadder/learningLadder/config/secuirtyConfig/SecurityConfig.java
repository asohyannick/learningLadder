package com.learningLadder.learningLadder.config.secuirtyConfig;
import com.learningLadder.learningLadder.config.apiResponse.APIErrorResponse;
import com.learningLadder.learningLadder.config.jwtFilter.JWTFilter;
import com.learningLadder.learningLadder.config.ratingLimiting.RateLimitingFilter;
import com.learningLadder.learningLadder.enums.userRole.UserRole;
import jakarta.servlet.DispatcherType;
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
            HEAD_OF_DEPARTMENT,
            DEPUTY_HEAD_OF_DEPARTMENT,
            PROFESSOR, LECTURER
    );

    private static final String[] COURSE_DELETE_ROLES = roles(
            SUPER_ADMIN,
            ADMIN,
            ACADEMIC_DIRECTOR
    );

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
            STUDENT,
            SUPER_ADMIN,
            ADMIN,
            IT_ADMIN
    );

    private static final String TUTOR_PROFILE_BASE = "/api/*/tutor-profile";

    private static final String[] TUTOR_PROFILE_READ_ROUTES = {
            TUTOR_PROFILE_BASE,
            TUTOR_PROFILE_BASE + "/search",
            TUTOR_PROFILE_BASE + "/count",
            TUTOR_PROFILE_BASE + "/download",
            TUTOR_PROFILE_BASE + "/*"
    };

    private static final String[] TUTOR_PROFILE_CREATE_ROUTES = { TUTOR_PROFILE_BASE };
    private static final String[] TUTOR_PROFILE_UPDATE_ROUTES = { TUTOR_PROFILE_BASE + "/*" };
    private static final String[] TUTOR_PROFILE_DELETE_ROUTES = { TUTOR_PROFILE_BASE + "/*" };

    private static final String[] TUTOR_PROFILE_STAFF_ROLES = roles(
            SUPER_ADMIN,
            ADMIN,
            IT_ADMIN,
            REGISTRAR,
            DEPUTY_REGISTRAR,
            ADMISSIONS_OFFICER,
            STUDENT_REGISTRAR
    );

    private static final String[] TUTOR_ROLES = roles(
            SUPER_ADMIN,
            LECTURER,
            PROFESSOR,
            ASSISTANT_LECTURER
    );

    private static final String[] TUTOR_PROFILE_MANAGE_ROLES = roles(
            LECTURER,
            PROFESSOR,
            ASSISTANT_LECTURER,
            SUPER_ADMIN,
            ADMIN,
            IT_ADMIN
    );

    private static final String TIMETABLE_BASE = "/api/*/time-table";

    private static final String[] TIMETABLE_READ_ROUTES = {
            TIMETABLE_BASE,
            TIMETABLE_BASE + "/search",
            TIMETABLE_BASE + "/count",
            TIMETABLE_BASE + "/download",
            TIMETABLE_BASE + "/*"
    };

    private static final String[] TIMETABLE_CREATE_ROUTES = { TIMETABLE_BASE };
    private static final String[] TIMETABLE_UPDATE_ROUTES = { TIMETABLE_BASE + "/*" };
    private static final String[] TIMETABLE_DELETE_ROUTES = { TIMETABLE_BASE + "/*" };

    private static final String[] TIMETABLE_WRITE_ROLES = roles(
            SUPER_ADMIN, ADMIN, IT_ADMIN,
            ACADEMIC_DIRECTOR,
            STUDENT,
            DEAN, DEPUTY_DEAN,
            HEAD_OF_DEPARTMENT, DEPUTY_HEAD_OF_DEPARTMENT,
            PROFESSOR, LECTURER);

    private static final String[] TIMETABLE_DELETE_ROLES = roles(
            SUPER_ADMIN, ADMIN, IT_ADMIN, ACADEMIC_DIRECTOR);


    private static final String REVIEW_BASE = "/api/*/review";

    private static final String[] REVIEW_READ_ROUTES = {
            REVIEW_BASE,
            REVIEW_BASE + "/search",
            REVIEW_BASE + "/count",
            REVIEW_BASE + "/*"
    };

    private static final String[] REVIEW_DOWNLOAD_ROUTES = { REVIEW_BASE + "/download" };
    private static final String[] REVIEW_CREATE_ROUTES = { REVIEW_BASE };
    private static final String[] REVIEW_UPDATE_ROUTES = { REVIEW_BASE + "/*" };
    private static final String[] REVIEW_DELETE_ROUTES = { REVIEW_BASE + "/*" };

    private static final String[] REVIEW_MODERATION_ROUTES = {
            REVIEW_BASE + "/*/approve",
            REVIEW_BASE + "/*/disapprove"
    };

    private static final String[] REVIEW_MODERATOR_ROLES = roles(
            SUPER_ADMIN, ADMIN, IT_ADMIN, ACADEMIC_DIRECTOR, STUDENT);

    private static final String[] REVIEW_APPROVAL_ROLES = roles(
            SUPER_ADMIN, ADMIN, IT_ADMIN, ACADEMIC_DIRECTOR, STUDENT);

    private static final String[] REVIEW_MANAGE_ROLES = roles(
             SUPER_ADMIN, ADMIN, IT_ADMIN, ACADEMIC_DIRECTOR);

    private static final String JOB_LISTING_BASE = "/api/*/job-listing";

    private static final String[] JOB_LISTING_READ_ROUTES = {
            JOB_LISTING_BASE,
            JOB_LISTING_BASE + "/search",
            JOB_LISTING_BASE + "/count",
            JOB_LISTING_BASE + "/download",
            JOB_LISTING_BASE + "/*"
    };

    private static final String[] JOB_LISTING_CREATE_ROUTES = { JOB_LISTING_BASE };
    private static final String[] JOB_LISTING_UPDATE_ROUTES = { JOB_LISTING_BASE + "/*" };
    private static final String[] JOB_LISTING_DELETE_ROUTES = { JOB_LISTING_BASE + "/*" };

    private static final String[] JOB_LISTING_MANAGE_ROLES = roles(SUPER_ADMIN, ADMIN, IT_ADMIN);

    private static final String JOB_APPLICATION_BASE = "/api/*/job-application";

    private static final String[] JOB_APPLICATION_ADMIN_READ_ROUTES = {
            JOB_APPLICATION_BASE,
            JOB_APPLICATION_BASE + "/search",
            JOB_APPLICATION_BASE + "/count",
            JOB_APPLICATION_BASE + "/download"
    };

    private static final String[] JOB_APPLICATION_SINGLE_READ_ROUTES = { JOB_APPLICATION_BASE + "/*" };

    private static final String[] JOB_APPLICATION_CREATE_ROUTES = { JOB_APPLICATION_BASE };
    private static final String[] JOB_APPLICATION_UPDATE_ROUTES = { JOB_APPLICATION_BASE + "/*" };
    private static final String[] JOB_APPLICATION_DELETE_ROUTES = { JOB_APPLICATION_BASE + "/*" };

    private static final String[] JOB_APPLICATION_MANAGE_ROLES = roles(SUPER_ADMIN, ADMIN, IT_ADMIN);

    private static final String BOOKING_BASE = "/api/*/booking";

    private static final String[] BOOKING_STAFF_READ_ROUTES = {
            BOOKING_BASE,
            BOOKING_BASE + "/search",
            BOOKING_BASE + "/count",
            BOOKING_BASE + "/download"
    };

    private static final String[] BOOKING_SINGLE_READ_ROUTES = { BOOKING_BASE + "/*" };
    private static final String[] BOOKING_CREATE_ROUTES = { BOOKING_BASE };
    private static final String[] BOOKING_UPDATE_ROUTES = { BOOKING_BASE + "/*" };
    private static final String[] BOOKING_DELETE_ROUTES = { BOOKING_BASE + "/*" };

    private static final String[] BOOKING_STAFF_ROLES = roles(SUPER_ADMIN, ADMIN, IT_ADMIN, ACADEMIC_DIRECTOR);

    private static final String LIBRARY_BASE = "/api/*/library";

    private static final String[] LIBRARY_BOOK_READ_ROUTES = {
            LIBRARY_BASE + "/books",
            LIBRARY_BASE + "/books/search",
            LIBRARY_BASE + "/books/count",
            LIBRARY_BASE + "/books/*"
    };
    private static final String[] LIBRARY_BOOK_CREATE_ROUTES = { LIBRARY_BASE + "/books" };
    private static final String[] LIBRARY_BOOK_UPDATE_ROUTES = { LIBRARY_BASE + "/books/*" };
    private static final String[] LIBRARY_BOOK_DELETE_ROUTES = { LIBRARY_BASE + "/books/*" };

    // Must be matched BEFORE "/loans/*", which would otherwise also match /search, /count and /download
    private static final String[] LIBRARY_LOAN_STAFF_READ_ROUTES = {
            LIBRARY_BASE + "/loans",
            LIBRARY_BASE + "/loans/search",
            LIBRARY_BASE + "/loans/count",
            LIBRARY_BASE + "/loans/download"
    };
    private static final String[] LIBRARY_LOAN_SINGLE_READ_ROUTES = { LIBRARY_BASE + "/loans/*" };
    private static final String[] LIBRARY_LOAN_BORROW_ROUTES = { LIBRARY_BASE + "/loans" };
    private static final String[] LIBRARY_LOAN_STAFF_ACTION_ROUTES = {
            LIBRARY_BASE + "/loans/*",
            LIBRARY_BASE + "/loans/*/issue",
            LIBRARY_BASE + "/loans/*/confirm-return"
    };
    private static final String[] LIBRARY_LOAN_STUDENT_ACTION_ROUTES = {
            LIBRARY_BASE + "/loans/*/renew",
            LIBRARY_BASE + "/loans/*/return"
    };
    private static final String[] LIBRARY_LOAN_SHARED_ACTION_ROUTES = {
            LIBRARY_BASE + "/loans/*/cancel",
            LIBRARY_BASE + "/loans/*/report-lost"
    };
    private static final String[] LIBRARY_LOAN_DELETE_ROUTES = { LIBRARY_BASE + "/loans/*" };

    private static final String[] LIBRARY_STAFF_ROLES = roles(SUPER_ADMIN, ADMIN, IT_ADMIN);
    private static final String[] LIBRARY_STUDENT_OR_STAFF_ROLES = roles(STUDENT, SUPER_ADMIN, ADMIN, IT_ADMIN);

    private static final String AI_ASSIST_BASE = "/api/*/ai-assist";

    private static final String[] AI_ASSIST_ADMIN_READ_ROUTES = { AI_ASSIST_BASE + "/assistants/admin/**" };
    private static final String[] AI_ASSIST_ADMIN_CREATE_ROUTES = { AI_ASSIST_BASE + "/assistants" };
    private static final String[] AI_ASSIST_ADMIN_WRITE_ROUTES = { AI_ASSIST_BASE + "/assistants/*" };
    private static final String[] AI_ASSIST_PUBLIC_READ_ROUTES = {
            AI_ASSIST_BASE + "/assistants",
            AI_ASSIST_BASE + "/assistants/search",
            AI_ASSIST_BASE + "/assistants/slug/*",
            AI_ASSIST_BASE + "/assistants/*/remaining-messages"
    };
    private static final String[] AI_ASSIST_CHAT_ROUTES = {
            AI_ASSIST_BASE + "/conversations",
            AI_ASSIST_BASE + "/conversations/**",
            AI_ASSIST_BASE + "/messages/*/feedback"
    };

    private static final String[] AI_ASSIST_ADMIN_ROLES = roles(SUPER_ADMIN, ADMIN, IT_ADMIN);

    private static final String PARTNER_BASE = "/api/*/partners";

    private static final String[] PARTNER_PUBLIC_READ_ROUTES = {
            PARTNER_BASE + "/public",
            PARTNER_BASE + "/public/search",
            PARTNER_BASE + "/public/*"
    };
    private static final String[] PARTNER_STAFF_READ_ROUTES = {
            PARTNER_BASE,
            PARTNER_BASE + "/search",
            PARTNER_BASE + "/count",
            PARTNER_BASE + "/download",
            PARTNER_BASE + "/*"
    };
    private static final String[] PARTNER_CREATE_ROUTES = { PARTNER_BASE };
    private static final String[] PARTNER_WRITE_ROUTES = { PARTNER_BASE + "/*" };

    private static final String[] PARTNER_STAFF_ROLES = roles(SUPER_ADMIN, ADMIN, IT_ADMIN);

    private static final String CART_BASE = "/api/*/shopping-cart";

    private static final String[] CART_STAFF_ROUTES = { CART_BASE + "/admin/**" };
    private static final String[] CART_USER_ROUTES = { CART_BASE, CART_BASE + "/**" };

    private static final String[] CART_STAFF_ROLES = roles(SUPER_ADMIN, ADMIN, IT_ADMIN);

    private static final String COUPON_BASE = "/api/*/coupons";

    private static final String[] COUPON_STUDENT_ROUTES = { COUPON_BASE + "/mine" };
    private static final String[] COUPON_ISSUER_ROUTES = { COUPON_BASE, COUPON_BASE + "/**" };

    private static final String[] COUPON_ISSUER_ROLES = roles(
            SUPER_ADMIN, ADMIN, IT_ADMIN,
            LECTURER, PROFESSOR, ASSISTANT_LECTURER);

    private static final String PAYMENT_BASE = "/api/*/payment";

    private static final String[] PAYMENT_WEBHOOK_ROUTES = { PAYMENT_BASE + "/webhook/stripe" };

    private static final String[] PAYMENT_FINANCE_ROUTES = {
            PAYMENT_BASE + "/school-fees/record",
            PAYMENT_BASE + "/salaries",
            PAYMENT_BASE + "/salaries/**",
            PAYMENT_BASE + "/search",
            PAYMENT_BASE + "/*/refund"
    };
    private static final String[] PAYMENT_USER_ROUTES = { PAYMENT_BASE, PAYMENT_BASE + "/**" };

    private static final String[] PAYMENT_FINANCE_ROLES = roles(SUPER_ADMIN, ADMIN);

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
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).hasAnyRole(PLATFORM_ADMIN_ROLES)

                        .requestMatchers(SWAGGER_WHITELIST).permitAll()

                        .requestMatchers(HttpMethod.GET, ADMIN_READ_ROUTES).hasAnyRole(PLATFORM_ADMIN_ROLES)

                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_ROUTES).permitAll()

                        // Courses
                        .requestMatchers(HttpMethod.GET, COURSE_READ_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.POST, COURSE_CREATE_ROUTES).hasAnyRole(COURSE_WRITE_ROLES)
                        .requestMatchers(HttpMethod.PATCH, COURSE_UPDATE_ROUTES).hasAnyRole(COURSE_WRITE_ROLES)
                        .requestMatchers(HttpMethod.DELETE, COURSE_DELETE_ROUTES).hasAnyRole(COURSE_DELETE_ROLES)

                        // Student profiles
                        .requestMatchers(HttpMethod.GET, STUDENT_PROFILE_READ_ROUTES).hasAnyRole(STUDENT_PROFILE_STAFF_ROLES)
                        .requestMatchers(HttpMethod.POST, STUDENT_PROFILE_CREATE_ROUTES).hasRole(STUDENT.name())
                        .requestMatchers(HttpMethod.PATCH, STUDENT_PROFILE_UPDATE_ROUTES).hasAnyRole(STUDENT_PROFILE_MANAGE_ROLES)
                        .requestMatchers(HttpMethod.DELETE, STUDENT_PROFILE_DELETE_ROUTES).hasAnyRole(STUDENT_PROFILE_MANAGE_ROLES)

                        // Tutor profiles
                        .requestMatchers(HttpMethod.GET, TUTOR_PROFILE_READ_ROUTES).hasAnyRole(TUTOR_PROFILE_STAFF_ROLES)
                        .requestMatchers(HttpMethod.POST, TUTOR_PROFILE_CREATE_ROUTES).hasAnyRole(TUTOR_ROLES)
                        .requestMatchers(HttpMethod.PATCH, TUTOR_PROFILE_UPDATE_ROUTES).hasAnyRole(TUTOR_PROFILE_MANAGE_ROLES)
                        .requestMatchers(HttpMethod.DELETE, TUTOR_PROFILE_DELETE_ROUTES).hasAnyRole(TUTOR_PROFILE_MANAGE_ROLES)

                        // Timetable
                        .requestMatchers(HttpMethod.GET, TIMETABLE_READ_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.POST, TIMETABLE_CREATE_ROUTES).hasAnyRole(TIMETABLE_WRITE_ROLES)
                        .requestMatchers(HttpMethod.PUT, TIMETABLE_UPDATE_ROUTES).hasAnyRole(TIMETABLE_WRITE_ROLES)
                        .requestMatchers(HttpMethod.DELETE, TIMETABLE_DELETE_ROUTES).hasAnyRole(TIMETABLE_DELETE_ROLES)

                        // Reviews
                        .requestMatchers(HttpMethod.GET, REVIEW_DOWNLOAD_ROUTES).hasAnyRole(REVIEW_MODERATOR_ROLES)
                        .requestMatchers(HttpMethod.GET, REVIEW_READ_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.POST, REVIEW_CREATE_ROUTES).hasRole(STUDENT.name())
                        .requestMatchers(HttpMethod.PATCH, REVIEW_MODERATION_ROUTES).hasAnyRole(REVIEW_MODERATOR_ROLES)
                        .requestMatchers(HttpMethod.PATCH, REVIEW_UPDATE_ROUTES).hasAnyRole(REVIEW_APPROVAL_ROLES)
                        .requestMatchers(HttpMethod.DELETE, REVIEW_DELETE_ROUTES).hasAnyRole(REVIEW_MANAGE_ROLES)

                        // Job listings
                        .requestMatchers(HttpMethod.GET, JOB_LISTING_READ_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.POST, JOB_LISTING_CREATE_ROUTES).hasAnyRole(JOB_LISTING_MANAGE_ROLES)
                        .requestMatchers(HttpMethod.PATCH, JOB_LISTING_UPDATE_ROUTES).hasAnyRole(JOB_LISTING_MANAGE_ROLES)
                        .requestMatchers(HttpMethod.DELETE, JOB_LISTING_DELETE_ROUTES).hasAnyRole(JOB_LISTING_MANAGE_ROLES)

                        // Job applications
                        .requestMatchers(HttpMethod.GET, JOB_APPLICATION_ADMIN_READ_ROUTES).hasAnyRole(JOB_APPLICATION_MANAGE_ROLES)
                        .requestMatchers(HttpMethod.GET, JOB_APPLICATION_SINGLE_READ_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.POST, JOB_APPLICATION_CREATE_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.PATCH, JOB_APPLICATION_UPDATE_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.DELETE, JOB_APPLICATION_DELETE_ROUTES).hasAnyRole(JOB_APPLICATION_MANAGE_ROLES)

                        // Bookings
                        .requestMatchers(HttpMethod.GET, BOOKING_STAFF_READ_ROUTES).hasAnyRole(BOOKING_STAFF_ROLES)
                        .requestMatchers(HttpMethod.GET, BOOKING_SINGLE_READ_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.POST, BOOKING_CREATE_ROUTES).hasRole(STUDENT.name())
                        .requestMatchers(HttpMethod.PATCH, BOOKING_UPDATE_ROUTES).hasRole(STUDENT.name())
                        .requestMatchers(HttpMethod.DELETE, BOOKING_DELETE_ROUTES).hasAnyRole(BOOKING_STAFF_ROLES)

                        // Library: books
                        .requestMatchers(HttpMethod.GET, LIBRARY_BOOK_READ_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.POST, LIBRARY_BOOK_CREATE_ROUTES).hasAnyRole(LIBRARY_STAFF_ROLES)
                        .requestMatchers(HttpMethod.PATCH, LIBRARY_BOOK_UPDATE_ROUTES).hasAnyRole(LIBRARY_STAFF_ROLES)
                        .requestMatchers(HttpMethod.DELETE, LIBRARY_BOOK_DELETE_ROUTES).hasAnyRole(LIBRARY_STAFF_ROLES)

                        // Library: loans
                        .requestMatchers(HttpMethod.GET, LIBRARY_LOAN_STAFF_READ_ROUTES).hasAnyRole(LIBRARY_STAFF_ROLES)
                        .requestMatchers(HttpMethod.GET, LIBRARY_LOAN_SINGLE_READ_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.POST, LIBRARY_LOAN_BORROW_ROUTES).hasRole(STUDENT.name())
                        .requestMatchers(HttpMethod.PATCH, LIBRARY_LOAN_STAFF_ACTION_ROUTES).hasAnyRole(LIBRARY_STAFF_ROLES)
                        .requestMatchers(HttpMethod.PATCH, LIBRARY_LOAN_STUDENT_ACTION_ROUTES).hasRole(STUDENT.name())
                        .requestMatchers(HttpMethod.PATCH, LIBRARY_LOAN_SHARED_ACTION_ROUTES).hasAnyRole(LIBRARY_STUDENT_OR_STAFF_ROLES)
                        .requestMatchers(HttpMethod.DELETE, LIBRARY_LOAN_DELETE_ROUTES).hasAnyRole(LIBRARY_STAFF_ROLES)

                        // AI assistants & chat
                        .requestMatchers(HttpMethod.GET, AI_ASSIST_ADMIN_READ_ROUTES).hasAnyRole(AI_ASSIST_ADMIN_ROLES)
                        .requestMatchers(HttpMethod.POST, AI_ASSIST_ADMIN_CREATE_ROUTES).hasAnyRole(AI_ASSIST_ADMIN_ROLES)
                        .requestMatchers(HttpMethod.PATCH, AI_ASSIST_ADMIN_WRITE_ROUTES).hasAnyRole(AI_ASSIST_ADMIN_ROLES)
                        .requestMatchers(HttpMethod.DELETE, AI_ASSIST_ADMIN_WRITE_ROUTES).hasAnyRole(AI_ASSIST_ADMIN_ROLES)
                        .requestMatchers(HttpMethod.GET, AI_ASSIST_PUBLIC_READ_ROUTES).authenticated()
                        .requestMatchers(AI_ASSIST_CHAT_ROUTES).authenticated()

                        .requestMatchers(HttpMethod.GET, PARTNER_PUBLIC_READ_ROUTES).permitAll()
                        .requestMatchers(HttpMethod.GET, PARTNER_STAFF_READ_ROUTES).hasAnyRole(PARTNER_STAFF_ROLES)
                        .requestMatchers(HttpMethod.POST, PARTNER_CREATE_ROUTES).hasAnyRole(PARTNER_STAFF_ROLES)
                        .requestMatchers(HttpMethod.PATCH, PARTNER_WRITE_ROUTES).hasAnyRole(PARTNER_STAFF_ROLES)
                        .requestMatchers(HttpMethod.DELETE, PARTNER_WRITE_ROUTES).hasAnyRole(PARTNER_STAFF_ROLES)

                        .requestMatchers(CART_STAFF_ROUTES).hasAnyRole(CART_STAFF_ROLES)
                        .requestMatchers(CART_USER_ROUTES).authenticated()
                        .requestMatchers(HttpMethod.GET, COUPON_STUDENT_ROUTES).authenticated()
                        .requestMatchers(COUPON_ISSUER_ROUTES).hasAnyRole(COUPON_ISSUER_ROLES)

                        .requestMatchers(HttpMethod.GET, PARTNER_PUBLIC_READ_ROUTES).permitAll()
                        .requestMatchers(HttpMethod.GET, PARTNER_STAFF_READ_ROUTES).hasAnyRole(PARTNER_STAFF_ROLES)
                        .requestMatchers(HttpMethod.POST, PARTNER_CREATE_ROUTES).hasAnyRole(PARTNER_STAFF_ROLES)
                        .requestMatchers(HttpMethod.PATCH, PARTNER_WRITE_ROUTES).hasAnyRole(PARTNER_STAFF_ROLES)
                        .requestMatchers(HttpMethod.DELETE, PARTNER_WRITE_ROUTES).hasAnyRole(PARTNER_STAFF_ROLES)


                        .requestMatchers(CART_STAFF_ROUTES).hasAnyRole(CART_STAFF_ROLES)
                        .requestMatchers(CART_USER_ROUTES).authenticated()

                        .requestMatchers(HttpMethod.GET, COUPON_STUDENT_ROUTES).authenticated()
                        .requestMatchers(COUPON_ISSUER_ROUTES).hasAnyRole(COUPON_ISSUER_ROLES)

                        .requestMatchers(HttpMethod.POST, PAYMENT_WEBHOOK_ROUTES).permitAll()
                        .requestMatchers(PAYMENT_FINANCE_ROUTES).hasAnyRole(PAYMENT_FINANCE_ROLES)
                        .requestMatchers(PAYMENT_USER_ROUTES).authenticated()

                        .anyRequest().authenticated())

                .addFilterAfter(rateLimitingFilter, CorsFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
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

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> {
            Object attr = request.getAttribute(JWTFilter.AUTH_ERROR_ATTR);
            if (attr instanceof JWTFilter.AuthError(String code, String message)) {
                writeError(request, response, HttpStatus.UNAUTHORIZED, code, message);
            } else {
                writeError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                        "Authentication is required to access this resource");
            }
        };
    }
}