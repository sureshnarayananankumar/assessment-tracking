package com.assesshub.domain.enums;

public enum AssignmentStatus {
    DRAFT, PUBLISHED, ACTIVE, COMPLETED, ARCHIVED;

    /** Whether candidates may start attempts (subject to schedule checks). */
    public boolean isOpen() {
        return this == PUBLISHED || this == ACTIVE;
    }
}
