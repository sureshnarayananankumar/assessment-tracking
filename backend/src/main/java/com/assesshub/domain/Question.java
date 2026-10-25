package com.assesshub.domain;

import com.assesshub.domain.enums.Difficulty;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.domain.enums.QuestionVisibility;
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
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "questions")
public class Question extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private QuestionType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "skill_category", length = 100)
    private String skillCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Difficulty difficulty;

    @Column(name = "default_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal defaultMarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private QuestionVisibility visibility = QuestionVisibility.PRIVATE;

    @Column(name = "coding_language", length = 40)
    private String codingLanguage;

    @Column(name = "starter_code", columnDefinition = "TEXT")
    private String starterCode;

    /** Model answer for evaluators. Never sent to candidates. */
    @Column(name = "reference_answer", columnDefinition = "TEXT")
    private String referenceAnswer;

    @Column(name = "evaluation_guidelines", columnDefinition = "TEXT")
    private String evaluationGuidelines;

    /** Newline separated accepted answers for auto-graded short answers. Never sent to candidates. */
    @Column(name = "accepted_answers", columnDefinition = "TEXT")
    private String acceptedAnswers;

    @Column(name = "case_sensitive", nullable = false)
    private boolean caseSensitive;

    @Column(name = "allowed_file_types", length = 255)
    private String allowedFileTypes;

    @Column(name = "max_file_size_mb")
    private Integer maxFileSizeMb;

    @Column(length = 255)
    private String tags;

    @Column(nullable = false)
    private boolean archived;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<QuestionOption> options = new ArrayList<>();

    @Version
    private long version;

    public void addOption(QuestionOption option) {
        option.setQuestion(this);
        options.add(option);
    }
}
