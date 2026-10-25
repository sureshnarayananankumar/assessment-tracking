package com.assesshub.domain.enums;

public enum RoleName {
    SUPER_ADMIN, ADMIN, RECRUITER, CANDIDATE;

    public boolean isStaff() {
        return this != CANDIDATE;
    }
}
