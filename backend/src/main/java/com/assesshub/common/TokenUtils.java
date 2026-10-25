package com.assesshub.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates unguessable URL-safe tokens and the SHA-256 hashes stored in the database.
 * Raw tokens are only ever e-mailed / returned once; the database keeps the hash.
 */
public final class TokenUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtils() {
    }

    /** 256 bits of entropy, base64url without padding (43 characters). */
    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Basic shape check so obviously malformed tokens are rejected before hitting the database. */
    public static boolean looksValid(String token) {
        return token != null && token.length() >= 32 && token.length() <= 128
                && token.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_');
    }
}
