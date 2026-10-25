package com.assesshub.domain.enums;

public enum UserStatus {
    /** Can sign in. */
    ACTIVE,
    /** Created by an invitation or an administrator; has not completed set-up yet. */
    INVITED,
    /** Soft-deleted / deactivated. Cannot sign in. */
    DISABLED
}
