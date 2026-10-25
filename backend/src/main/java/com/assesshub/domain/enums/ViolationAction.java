package com.assesshub.domain.enums;

public enum ViolationAction {
    /** Keep warning the candidate; take no further action. */
    WARN_ONLY,
    /** Flag the attempt for human review (default). Never an automatic cheating verdict. */
    FLAG_FOR_REVIEW,
    /** Submit the attempt automatically with the answers saved so far and flag it. */
    AUTO_SUBMIT
}
