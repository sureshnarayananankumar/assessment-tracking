package com.assesshub.service;

import com.assesshub.common.PageResponse;
import com.assesshub.common.TextUtils;
import com.assesshub.common.exception.ApiException;
import com.assesshub.domain.Question;
import com.assesshub.domain.QuestionOption;
import com.assesshub.domain.User;
import com.assesshub.domain.enums.Difficulty;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.domain.enums.QuestionVisibility;
import com.assesshub.dto.QuestionDtos.OptionRequest;
import com.assesshub.dto.QuestionDtos.QuestionDto;
import com.assesshub.dto.QuestionDtos.QuestionRequest;
import com.assesshub.repository.AssessmentAnswerRepository;
import com.assesshub.repository.AssignmentRepository;
import com.assesshub.repository.QuestionRepository;
import com.assesshub.repository.UserRepository;
import com.assesshub.security.UserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Question bank management with per-type validation.
 */
@Service
public class QuestionService {

    private static final int MAX_OPTIONS = 12;

    private final QuestionRepository questionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssessmentAnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;
    private final QuestionMapper mapper;
    private final FileStorageService storage;
    private final AuditService auditService;

    public QuestionService(QuestionRepository questionRepository, AssignmentRepository assignmentRepository,
                           AssessmentAnswerRepository answerRepository, UserRepository userRepository,
                           AccessGuard accessGuard, QuestionMapper mapper, FileStorageService storage,
                           AuditService auditService) {
        this.questionRepository = questionRepository;
        this.assignmentRepository = assignmentRepository;
        this.answerRepository = answerRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
        this.mapper = mapper;
        this.storage = storage;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionDto> search(UserPrincipal principal, String search, QuestionType type,
                                            Difficulty difficulty, String skill, Pageable pageable) {
        String skillLower = TextUtils.trimToNull(skill) == null ? null : skill.trim().toLowerCase(Locale.ROOT);
        return PageResponse.from(
                questionRepository.search(accessGuard.ownerScope(principal), type, difficulty, skillLower,
                        TextUtils.likePattern(search), pageable),
                q -> mapper.toStaffDto(q, principal));
    }

    @Transactional(readOnly = true)
    public List<String> skillCategories() {
        return questionRepository.findSkillCategories();
    }

    @Transactional(readOnly = true)
    public QuestionDto get(Long id, UserPrincipal principal) {
        Question q = find(id);
        accessGuard.assertCanView(principal, q);
        return mapper.toStaffDto(q, principal);
    }

    @Transactional
    public QuestionDto create(QuestionRequest request, UserPrincipal principal) {
        User owner = userRepository.getReferenceById(principal.getId());
        Question q = new Question();
        q.setOwner(owner);
        apply(q, request);
        questionRepository.save(q);
        auditService.log("QUESTION_CREATED", "QUESTION", q.getId(), q.getType() + ": " + q.getTitle());
        return mapper.toStaffDto(questionRepository.findById(q.getId()).orElseThrow(), principal);
    }

    @Transactional
    public QuestionDto update(Long id, QuestionRequest request, UserPrincipal principal) {
        Question q = find(id);
        accessGuard.assertCanEdit(principal, q);
        if (q.isArchived()) {
            throw ApiException.conflict("QUESTION_ARCHIVED", "Archived questions cannot be edited");
        }
        if (answerRepository.existsByQuestionId(id)) {
            throw ApiException.conflict("QUESTION_IN_USE",
                    "This question has already been answered by candidates. Duplicate it to make changes.");
        }
        apply(q, request);
        auditService.log("QUESTION_UPDATED", "QUESTION", q.getId(), q.getTitle());
        return mapper.toStaffDto(q, principal);
    }

    /** Deletes an unused question; archives it if any assessment or attempt references it. */
    @Transactional
    public String delete(Long id, UserPrincipal principal) {
        Question q = find(id);
        accessGuard.assertCanEdit(principal, q);
        if (assignmentRepository.isQuestionUsed(id) || answerRepository.existsByQuestionId(id)) {
            q.setArchived(true);
            auditService.log("QUESTION_ARCHIVED", "QUESTION", id, q.getTitle());
            return "Question is used by an assessment, so it was archived instead of deleted";
        }
        questionRepository.delete(q);
        auditService.log("QUESTION_DELETED", "QUESTION", id, q.getTitle());
        return "Question deleted";
    }

    @Transactional
    public QuestionDto duplicate(Long id, UserPrincipal principal) {
        Question src = find(id);
        accessGuard.assertCanView(principal, src);
        Question copy = new Question();
        copy.setOwner(userRepository.getReferenceById(principal.getId()));
        copy.setType(src.getType());
        copy.setTitle(TextUtils.truncate(src.getTitle() + " (copy)", 255));
        copy.setBody(src.getBody());
        copy.setSkillCategory(src.getSkillCategory());
        copy.setDifficulty(src.getDifficulty());
        copy.setDefaultMarks(src.getDefaultMarks());
        copy.setVisibility(QuestionVisibility.PRIVATE);
        copy.setCodingLanguage(src.getCodingLanguage());
        copy.setStarterCode(src.getStarterCode());
        copy.setReferenceAnswer(src.getReferenceAnswer());
        copy.setEvaluationGuidelines(src.getEvaluationGuidelines());
        copy.setAcceptedAnswers(src.getAcceptedAnswers());
        copy.setCaseSensitive(src.isCaseSensitive());
        copy.setAllowedFileTypes(src.getAllowedFileTypes());
        copy.setMaxFileSizeMb(src.getMaxFileSizeMb());
        copy.setTags(src.getTags());
        src.getOptions().forEach(o -> copy.addOption(new QuestionOption(o.getOptionText(), o.isCorrect(), o.getDisplayOrder())));
        questionRepository.save(copy);
        auditService.log("QUESTION_DUPLICATED", "QUESTION", copy.getId(), "From question " + id);
        return mapper.toStaffDto(copy, principal);
    }

    Question find(Long id) {
        return questionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Question"));
    }

    private void apply(Question q, QuestionRequest r) {
        QuestionType type = r.type();
        q.setType(type);
        q.setTitle(r.title().trim());
        q.setBody(r.body().trim());
        q.setSkillCategory(TextUtils.trimToNull(r.skillCategory()));
        q.setDifficulty(r.difficulty());
        q.setDefaultMarks(r.defaultMarks().setScale(2, RoundingMode.HALF_UP));
        q.setVisibility(r.visibility() == null ? QuestionVisibility.PRIVATE : r.visibility());
        q.setTags(TextUtils.trimToNull(r.tags()));
        q.setReferenceAnswer(TextUtils.trimToNull(r.referenceAnswer()));
        q.setEvaluationGuidelines(TextUtils.trimToNull(r.evaluationGuidelines()));
        q.setCaseSensitive(r.caseSensitive());

        q.setCodingLanguage(type == QuestionType.CODING ? TextUtils.trimToNull(r.codingLanguage()) : null);
        q.setStarterCode(type == QuestionType.CODING ? r.starterCode() : null);
        q.setAcceptedAnswers(type == QuestionType.SHORT_ANSWER ? TextUtils.joinLines(r.acceptedAnswers()) : null);
        if (type == QuestionType.FILE_UPLOAD) {
            q.setAllowedFileTypes(storage.normalizeExtensions(r.allowedFileTypes()));
            q.setMaxFileSizeMb(r.maxFileSizeMb() == null ? 10 : Math.min(r.maxFileSizeMb(), 20));
        } else {
            q.setAllowedFileTypes(null);
            q.setMaxFileSizeMb(null);
        }

        q.getOptions().clear();
        if (type.usesOptions()) {
            List<OptionRequest> options = r.options() == null ? List.of() : r.options();
            validateOptions(type, options);
            for (int i = 0; i < options.size(); i++) {
                String text = type == QuestionType.TRUE_FALSE ? (i == 0 ? "True" : "False") : options.get(i).text().trim();
                q.addOption(new QuestionOption(text, options.get(i).correct(), i));
            }
        }
    }

    private static void validateOptions(QuestionType type, List<OptionRequest> options) {
        long correct = options.stream().filter(OptionRequest::correct).count();
        switch (type) {
            case TRUE_FALSE -> {
                if (options.size() != 2 || correct != 1) {
                    throw ApiException.badRequest("INVALID_OPTIONS",
                            "True/false questions need exactly two options (True, False) with one marked correct");
                }
            }
            case MCQ_SINGLE -> {
                if (options.size() < 2 || options.size() > MAX_OPTIONS || correct != 1) {
                    throw ApiException.badRequest("INVALID_OPTIONS",
                            "Single-choice questions need 2-" + MAX_OPTIONS + " options with exactly one correct");
                }
            }
            case MCQ_MULTI -> {
                if (options.size() < 2 || options.size() > MAX_OPTIONS || correct < 1) {
                    throw ApiException.badRequest("INVALID_OPTIONS",
                            "Multiple-select questions need 2-" + MAX_OPTIONS + " options with at least one correct");
                }
            }
            default -> {
            }
        }
        if (type != QuestionType.TRUE_FALSE) {
            Set<String> seen = new HashSet<>();
            for (OptionRequest o : options) {
                if (!seen.add(o.text().trim().toLowerCase(Locale.ROOT))) {
                    throw ApiException.badRequest("DUPLICATE_OPTION", "Options must be unique");
                }
            }
        }
    }

    static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
