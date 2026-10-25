package com.assesshub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Strongly typed application settings bound from the {@code app.*} namespace.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendUrl,
        Cors cors,
        Jwt jwt,
        Cookie cookie,
        Mail mail,
        Security security,
        Storage storage,
        Bootstrap bootstrap,
        DemoData demoData,
        Scheduler scheduler) {

    public record Cors(List<String> allowedOrigins) {
    }

    public record Jwt(String secret, String issuer, long accessTokenMinutes, long refreshTokenDays) {
    }

    public record Cookie(boolean secure, String sameSite) {
    }

    public record Mail(boolean enabled, String from, String fromName, boolean logLinks) {
    }

    public record Security(int maxFailedLogins, int lockoutMinutes, int passwordResetMinutes,
                           int accountSetupHours, RateLimit rateLimit) {
    }

    public record RateLimit(int authRequestsPerMinute) {
    }

    public record Storage(String uploadDir, List<String> allowedExtensions) {
    }

    public record Bootstrap(String adminEmail, String adminPassword, String adminName) {
    }

    public record DemoData(boolean enabled, String password) {
    }

    public record Scheduler(boolean enabled, long expiryCheckMs, long maintenanceMs) {
    }

    public String frontendBaseUrl() {
        String url = frontendUrl == null ? "" : frontendUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
