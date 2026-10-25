package com.assesshub.domain;

import com.assesshub.domain.enums.AssessmentType;
import com.assesshub.domain.enums.AssignmentStatus;
import com.assesshub.domain.enums.Difficulty;
import com.assesshub.domain.enums.ReleasePolicy;
import com.assesshub.domain.enums.ViolationAction;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An assignment / assessment definition: content, schedule, scoring and monitoring policy.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assignments")
public class Assignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "evaluation_criteria", columnDefinition = "TEXT")
    private String evaluationCriteria;

    @Column(name = "skill_category", length = 100)
    private String skillCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, columnDefinition = "varchar(30)")
    private AssessmentType assessmentType;

    @Column(name = "total_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalMarks = BigDecimal.ZERO;

    @Column(name = "passing_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal passingMarks = BigDecimal.ZERO;

    @Column(name = "start_at")
    private Instant startAt;

    /** Assessment window closes (deadline). */
    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 1;

    @Column(name = "grace_period_seconds", nullable = false)
    private int gracePeriodSeconds = 30;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions;

    @Column(name = "shuffle_options", nullable = false)
    private boolean shuffleOptions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AssignmentStatus status = AssignmentStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "results_release_policy", nullable = false, columnDefinition = "varchar(20)")
    private ReleasePolicy resultsReleasePolicy = ReleasePolicy.AFTER_GRADING;

    @Column(name = "show_correct_answers", nullable = false)
    private boolean showCorrectAnswers;

    @Column(name = "results_released", nullable = false)
    private boolean resultsReleased;

    @Column(name = "results_released_at")
    private Instant resultsReleasedAt;

    @Column(name = "monitoring_enabled", nullable = false)
    private boolean monitoringEnabled = true;

    @Column(name = "warning_threshold", nullable = false)
    private int warningThreshold = 3;

    @Column(name = "max_violations", nullable = false)
    private int maxViolations = 5;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_action", nullable = false, columnDefinition = "varchar(20)")
    private ViolationAction violationAction = ViolationAction.FLAG_FOR_REVIEW;

    @Column(name = "published_at")
    private Instant publishedAt;

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<AssignmentQuestion> questions = new ArrayList<>();

    @Version
    private long version;

    public void recalculateTotalMarks() {
        this.totalMarks = questions.stream()
                .map(AssignmentQuestion::getMarks)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
