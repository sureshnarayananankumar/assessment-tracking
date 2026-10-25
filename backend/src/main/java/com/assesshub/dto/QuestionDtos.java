package com.assesshub.dto;

import com.assesshub.domain.enums.Difficulty;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.domain.enums.QuestionVisibility;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record OptionRequest(
            @NotBlank @Size(max = 2000) String text,
            boolean correct) {
    }

    public record QuestionRequest(
            @NotNull QuestionType type,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 20000) String body,
            @Size(max = 100) String skillCategory,
            @NotNull Difficulty difficulty,
            @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") BigDecimal defaultMarks,
            QuestionVisibility visibility,
            @Size(max = 40) String codingLanguage,
            @Size(max = 20000) String starterCode,
            @Size(max = 20000) String referenceAnswer,
            @Size(max = 5000) String evaluationGuidelines,
            @Size(max = 20) List<@Size(max = 500) String> acceptedAnswers,
            boolean caseSensitive,
            @Size(max = 255) String allowedFileTypes,
            @Min(1) @Max(50) Integer maxFileSizeMb,
            @Size(max = 255) String tags,
            @Valid @Size(max = 12) List<OptionRequest> options) {
    }

    /** {@code correct} is only populated for staff views. */
    public record OptionDto(Long id, String text, Boolean correct, int displayOrder) {
    }

    public record QuestionDto(
            Long id,
            QuestionType type,
            String title,
            String body,
            String skillCategory,
            Difficulty difficulty,
            BigDecimal defaultMarks,
            QuestionVisibility visibility,
            String codingLanguage,
            String starterCode,
            String referenceAnswer,
            String evaluationGuidelines,
            List<String> acceptedAnswers,
            boolean caseSensitive,
            String allowedFileTypes,
            Integer maxFileSizeMb,
            String tags,
            boolean archived,
            Long ownerId,
            String ownerName,
            boolean editable,
            List<OptionDto> options,
            Instant createdAt,
            Instant updatedAt) {
    }
}
