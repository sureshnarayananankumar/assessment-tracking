package com.assesshub.domain.enums;

public enum ActivityEventType {
    // Reported by the browser
    TAB_HIDDEN, TAB_VISIBLE, WINDOW_BLUR, WINDOW_FOCUS, FULLSCREEN_EXIT, COPY_ATTEMPT, PASTE_ATTEMPT, WARNING_SHOWN,
    // Recorded by the server only
    STARTED, RESUMED, SUBMITTED, AUTO_SUBMITTED, TERMINATED, FLAGGED;

    public boolean isClientReportable() {
        return ordinal() <= WARNING_SHOWN.ordinal();
    }

    /** Events that may count as a focus violation (subject to de-duplication). */
    public boolean isPotentialViolation() {
        return this == TAB_HIDDEN || this == WINDOW_BLUR;
    }

    public boolean isReturn() {
        return this == TAB_VISIBLE || this == WINDOW_FOCUS;
    }
}
