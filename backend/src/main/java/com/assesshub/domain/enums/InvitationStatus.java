package com.assesshub.domain.enums;

public enum InvitationStatus {
    PENDING, ACCEPTED, REVOKED, EXPIRED;

    public boolean isUsable() {
        return this == PENDING || this == ACCEPTED;
    }
}
