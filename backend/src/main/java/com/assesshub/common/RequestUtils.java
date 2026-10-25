package com.assesshub.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Access to the current HTTP request outside controllers (audit logging, attempt metadata).
 * The remote address already reflects X-Forwarded-For from trusted internal proxies only
 * (server.forward-headers-strategy=native).
 */
public final class RequestUtils {

    private RequestUtils() {
    }

    public static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.of(attrs.getRequest());
        }
        return Optional.empty();
    }

    public static String clientIp() {
        return currentRequest().map(HttpServletRequest::getRemoteAddr).map(ip -> TextUtils.truncate(ip, 45)).orElse(null);
    }

    public static String userAgent() {
        return currentRequest().map(r -> r.getHeader("User-Agent")).map(ua -> TextUtils.truncate(ua, 512)).orElse(null);
    }
}
