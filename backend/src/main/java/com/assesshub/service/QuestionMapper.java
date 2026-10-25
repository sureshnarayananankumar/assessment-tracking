package com.assesshub.service;

import com.assesshub.common.TextUtils;
import com.assesshub.domain.Question;
import com.assesshub.dto.QuestionDtos.OptionDto;
import com.assesshub.dto.QuestionDtos.QuestionDto;
import com.assesshub.security.UserPrincipal;
import org.springframework.stereotype.Component;

/**
 * Maps questions for staff views (includes answer keys). Candidate-facing mappings live in
 * {@link AttemptService} and never include correct answers.
 */
@Component
public class QuestionMapper {

    private final AccessGuard accessGuard;

    public QuestionMapper(AccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    public QuestionDto toStaffDto(Question q, UserPrincipal viewer) {
        return new QuestionDto(
                q.getId(), q.getType(), q.getTitle(), q.getBody(), q.getSkillCategory(), q.getDifficulty(),
                q.getDefaultMarks(), q.getVisibility(), q.getCodingLanguage(), q.getStarterCode(),
                q.getReferenceAnswer(), q.getEvaluationGuidelines(), TextUtils.splitLines(q.getAcceptedAnswers()),
                q.isCaseSensitive(), q.getAllowedFileTypes(), q.getMaxFileSizeMb(), q.getTags(), q.isArchived(),
                q.getOwner().getId(), q.getOwner().getFullName(),
                viewer != null && accessGuard.canEdit(viewer, q),
                q.getOptions().stream()
                        .map(o -> new OptionDto(o.getId(), o.getOptionText(), o.isCorrect(), o.getDisplayOrder()))
                        .toList(),
                q.getCreatedAt(), q.getUpdatedAt());
    }
}
