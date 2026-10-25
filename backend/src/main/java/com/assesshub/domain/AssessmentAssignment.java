package com.assesshub.domain;

import com.assesshub.domain.enums.AssigneeStatus;
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
 * Authorises one user to take one assessment ("assignee").
 * Unique per (assignment, user) which prevents duplicate invitations.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assessment_assignments")
public class AssessmentAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AssigneeStatus status = AssigneeStatus.ASSIGNED;

    /** Optional per-assignee deadline that overrides the assessment end date. */
    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "attempts_used", nullable = false)
    private int attemptsUsed;

    @Column(name = "reminder_count", nullable = false)
    private int reminderCount;

    @Column(name = "last_reminder_at")
    private Instant lastReminderAt;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    /** The deadline that applies to this assignee (override or assessment end date), may be null. */
    public Instant effectiveDeadline() {
        return dueAt != null ? dueAt : assignment.getEndAt();
    }
}
