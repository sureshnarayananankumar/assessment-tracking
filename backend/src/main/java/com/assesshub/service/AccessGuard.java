package com.assesshub.service;

import com.assesshub.common.exception.ApiException;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.Question;
import com.assesshub.domain.enums.QuestionVisibility;
import com.assesshub.security.UserPrincipal;
import org.springframework.stereotype.Component;

/**
 * Record-level authorisation (protection against insecure direct object references).
 * Unauthorised access to an existing record is reported as 404 so record ids cannot be probed.
 */
@Component
public class AccessGuard {

    /** Null means "no owner restriction" (administrators); otherwise restrict to the caller. */
    public Long ownerScope(UserPrincipal principal) {
        return principal.isAdminLevel() ? null : principal.getId();
    }

    public void requireStaff(UserPrincipal principal) {
        if (!principal.isStaff()) {
            throw ApiException.forbidden("Staff access required");
        }
    }

    public boolean canManage(UserPrincipal principal, Assignment assignment) {
        return principal.isAdminLevel()
                || (principal.isStaff() && assignment.getOwner().getId().equals(principal.getId()));
    }

    public void assertCanManage(UserPrincipal principal, Assignment assignment) {
        if (!canManage(principal, assignment)) {
            throw ApiException.notFound("Assessment");
        }
    }

    public boolean canView(UserPrincipal principal, Question question) {
        return principal.isAdminLevel()
                || question.getOwner().getId().equals(principal.getId())
                || (principal.isStaff() && question.getVisibility() == QuestionVisibility.SHARED);
    }

    public boolean canEdit(UserPrincipal principal, Question question) {
        return principal.isAdminLevel() || question.getOwner().getId().equals(principal.getId());
    }

    public void assertCanView(UserPrincipal principal, Question question) {
        if (!principal.isStaff() || !canView(principal, question)) {
            throw ApiException.notFound("Question");
        }
    }

    public void assertCanEdit(UserPrincipal principal, Question question) {
        assertCanView(principal, question);
        if (!canEdit(principal, question)) {
            throw ApiException.forbidden("Only the owner or an administrator can modify this question");
        }
    }
}
