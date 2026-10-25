package com.assesshub.domain.enums;

public enum AttemptStatus {
    IN_PROGRESS,
    /** Submitted by the candidate before the deadline. */
    SUBMITTED,
    /** Submitted by the server because time ran out. */
    AUTO_SUBMITTED,
    /** Ended by the server because of the monitoring policy or revoked access. */
    TERMINATED;

    public boolean isFinal() {
        return this != IN_PROGRESS;
    }
}
