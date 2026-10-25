package com.assesshub.web;

import com.assesshub.common.TextUtils;
import com.assesshub.config.AppProperties;
import com.assesshub.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Helpers for cookies, client metadata and file downloads.
 */
@Component
public class WebUtils {

    public static final String REFRESH_COOKIE = "ah_refresh";
    public static final String REFRESH_COOKIE_PATH = "/api/auth";

    private final AppProperties props;

    public WebUtils(AppProperties props) {
        this.props = props;
    }

    public ResponseCookie refreshCookie(AuthService.AuthResult auth) {
        return ResponseCookie.from(REFRESH_COOKIE, auth.refreshToken())
                .httpOnly(true)
                .secure(props.cookie().secure())
                .sameSite(props.cookie().sameSite())
                .path(REFRESH_COOKIE_PATH)
                .maxAge(auth.refreshTtl())
                .build();
    }

    public ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(props.cookie().secure())
                .sameSite(props.cookie().sameSite())
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    public static String ip(HttpServletRequest request) {
        return TextUtils.truncate(request.getRemoteAddr(), 45);
    }

    public static String userAgent(HttpServletRequest request) {
        return TextUtils.truncate(request.getHeader(HttpHeaders.USER_AGENT), 512);
    }

    /** Files are always delivered as attachments with a neutral content type (never rendered inline). */
    public static ResponseEntity<Resource> download(Resource resource, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    public static ResponseEntity<byte[]> csv(String content, String fileName) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
