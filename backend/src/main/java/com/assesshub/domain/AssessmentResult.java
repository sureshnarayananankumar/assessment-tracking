package com.assesshub.domain;

import com.assesshub.domain.enums.ResultGradingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assessment_results")
public class AssessmentResult extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true)
    private AssessmentAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "auto_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal autoScore = BigDecimal.ZERO;

    @Column(name = "manual_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal manualScore = BigDecimal.ZERO;

    @Column(name = "total_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalScore = BigDecimal.ZERO;

    @Column(name = "max_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxScore = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage = BigDecimal.ZERO;

    /** Null while grading is incomplete. */
    @Column
    private Boolean passed;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_status", nullable = false, columnDefinition = "varchar(20)")
    private ResultGradingStatus gradingStatus = ResultGradingStatus.PENDING_REVIEW;

    @Column(nullable = false)
    private boolean released;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @Column(name = "tab_switch_count", nullable = false)
    private int tabSwitchCount;

    @Column(name = "focus_loss_count", nullable = false)
    private int focusLossCount;

    @Column(name = "violation_count", nullable = false)
    private int violationCount;

    @Column(nullable = false)
    private boolean flagged;

    /** JSON summary of monitoring activity captured when the attempt was finalised. */
    @Column(name = "activity_summary", columnDefinition = "TEXT")
    private String activitySummary;

    @Version
    private long version;
}
