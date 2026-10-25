package com.assesshub.dto;

/**
 * Password rules shared by every endpoint that sets a password.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;
    /** At least one letter and one digit. */
    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String MESSAGE = "Password must be 8-128 characters and contain letters and numbers";

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        return password != null && password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH
                && password.matches(REGEX);
    }
}
