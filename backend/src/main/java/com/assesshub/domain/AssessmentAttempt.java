package com.assesshub.domain;

import com.assesshub.domain.enums.AttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One timed attempt. {@code startedAt} and {@code deadlineAt} are computed and stored by the
 * server, which is the single source of truth for timing.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assessment_attempts")
public class AssessmentAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_assignment_id", nullable = false)
    private AssessmentAssignment assessmentAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submission_reason", length = 255)
    private String submissionReason;

    /** Comma separated question ids in the order presented to this candidate. */
    @Column(name = "question_order", nullable = false, columnDefinition = "TEXT")
    private String questionOrder;

    /** Per-question option order, format "questionId:optId,optId;questionId:...". */
    @Column(name = "option_order", columnDefinition = "TEXT")
    private String optionOrder;

    @Column(name = "tab_switch_count", nullable = false)
    private int tabSwitchCount;

    @Column(name = "focus_loss_count", nullable = false)
    private int focusLossCount;

    @Column(name = "violation_count", nullable = false)
    private int violationCount;

    @Column(name = "total_away_ms", nullable = false)
    private long totalAwayMs;

    @Column(name = "last_violation_at")
    private Instant lastViolationAt;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "flag_reason", length = 255)
    private String flagReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "last_saved_at")
    private Instant lastSavedAt;

    @Version
    private long version;

    /** Deadline plus the configured grace period: writes after this instant are rejected. */
    public Instant hardDeadline() {
        return deadlineAt.plusSeconds(assignment.getGracePeriodSeconds());
    }
}
