package com.assesshub.security;

import com.assesshub.common.ApiError;
import com.assesshub.config.AppProperties;
import com.assesshub.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Stateless JWT security. URL rules give coarse role gates; fine-grained ownership checks
 * (IDOR protection) are enforced in the service layer via {@code AccessGuard}.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] STAFF = {"SUPER_ADMIN", "ADMIN", "RECRUITER"};
    private static final String[] ADMINS = {"SUPER_ADMIN", "ADMIN"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   UserRepository userRepository,
                                                   AppProperties props,
                                                   ObjectMapper objectMapper,
                                                   Clock clock) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, userRepository);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(
                props.security().rateLimit().authRequestsPerMinute(), objectMapper, clock);

        http
                // Stateless bearer-token API: no session cookies to protect with CSRF tokens.
                // The refresh cookie is SameSite=Strict, path-scoped, and the refresh endpoint
                // additionally requires a custom header (see AuthController).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(props)))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeError(res, objectMapper, 401, "Unauthorized", "UNAUTHORIZED",
                                        "Authentication is required", req.getRequestURI()))
                        .accessDeniedHandler((req, res, e) ->
                                writeError(res, objectMapper, 403, "Forbidden", "FORBIDDEN",
                                        "You do not have permission to perform this action", req.getRequestURI())))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/api/auth/reset-password/validate").permitAll()
                        .requestMatchers("/api/invitations/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/settings/public").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/users/**", "/api/audit-logs/**", "/api/email-notifications/**").hasAnyRole(ADMINS)
                        .requestMatchers(HttpMethod.GET, "/api/settings", "/api/settings/**").hasAnyRole(STAFF)
                        .requestMatchers("/api/settings/**", "/api/settings").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/questions/**", "/api/assignments/**", "/api/monitoring/**",
                                "/api/reports/**", "/api/dashboard/staff").hasAnyRole(STAFF)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeError(HttpServletResponse res, ObjectMapper mapper, int status, String error,
                                   String code, String message, String path) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ApiError.of(status, error, code, message, path));
    }

    private CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = props.cors().allowedOrigins() == null ? List.of()
                : props.cors().allowedOrigins().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Request-Id"));
        config.setExposedHeaders(List.of("Content-Disposition", "X-Request-Id", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Prevents Spring Boot from creating a default in-memory user with a generated password. */
    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByEmailIgnoreCase(username)
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
