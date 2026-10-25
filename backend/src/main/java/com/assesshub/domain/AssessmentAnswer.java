package com.assesshub.domain;

import com.assesshub.domain.enums.AnswerGradingStatus;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per question per attempt, created when the attempt starts (this also snapshots
 * the marks available for the question at that moment).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assessment_answers")
public class AssessmentAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private AssessmentAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Sorted comma separated option ids. */
    @Column(name = "selected_option_ids", length = 1000)
    private String selectedOptionIds;

    @Column(name = "text_answer", columnDefinition = "LONGTEXT")
    private String textAnswer;

    @Column(name = "code_answer", columnDefinition = "LONGTEXT")
    private String codeAnswer;

    @Column(name = "code_language", length = 40)
    private String codeLanguage;

    @Column(name = "file_original_name", length = 255)
    private String fileOriginalName;

    @Column(name = "file_stored_name", length = 100)
    private String fileStoredName;

    @Column(name = "file_content_type", length = 100)
    private String fileContentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "max_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "marks_awarded", precision = 8, scale = 2)
    private BigDecimal marksAwarded;

    @Column(name = "is_correct")
    private Boolean correct;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_status", nullable = false, columnDefinition = "varchar(20)")
    private AnswerGradingStatus gradingStatus = AnswerGradingStatus.PENDING;

    @Column(name = "evaluator_comment", columnDefinition = "TEXT")
    private String evaluatorComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @Column(name = "saved_at")
    private Instant savedAt;

    @Version
    private long version;

    public boolean isAnswered() {
        return (selectedOptionIds != null && !selectedOptionIds.isBlank())
                || (textAnswer != null && !textAnswer.isBlank())
                || (codeAnswer != null && !codeAnswer.isBlank())
                || fileStoredName != null;
    }
}
