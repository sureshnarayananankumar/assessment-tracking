package com.assesshub.service;

import com.assesshub.common.PageResponse;
import com.assesshub.common.TextUtils;
import com.assesshub.common.exception.ApiException;
import com.assesshub.domain.AssessmentResult;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.AssignmentAttachment;
import com.assesshub.domain.AssignmentQuestion;
import com.assesshub.domain.Question;
import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.AssignmentStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.ReleasePolicy;
import com.assesshub.domain.enums.ViolationAction;
import com.assesshub.dto.AssignmentDtos.AssignmentDto;
import com.assesshub.dto.AssignmentDtos.AssignmentQuestionDto;
import com.assesshub.dto.AssignmentDtos.AssignmentQuestionRequest;
import com.assesshub.dto.AssignmentDtos.AssignmentRequest;
import com.assesshub.dto.AssignmentDtos.AttachmentDto;
import com.assesshub.repository.AssessmentAssignmentRepository;
import com.assesshub.repository.AssessmentAttemptRepository;
import com.assesshub.repository.AssessmentInvitationRepository;
import com.assesshub.repository.AssessmentResultRepository;
import com.assesshub.repository.AssignmentAttachmentRepository;
import com.assesshub.repository.AssignmentRepository;
import com.assesshub.repository.QuestionRepository;
import com.assesshub.repository.UserRepository;
import com.assesshub.security.UserPrincipal;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Assessment definitions: CRUD, question composition, publication lifecycle, results release
 * and reference attachments.
 */
@Service
public class AssignmentService {

    public static final String ATTACHMENT_AREA = "attachments";
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024 * 1024;

    private final AssignmentRepository assignmentRepository;
    private final QuestionRepository questionRepository;
    private final AssessmentAssignmentRepository assigneeRepository;
    private final AssessmentInvitationRepository invitationRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentResultRepository resultRepository;
    private final AssignmentAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;
    private final QuestionMapper questionMapper;
    private final SettingsService settings;
    private final FileStorageService storage;
    private final GradingService gradingService;
    private final AuditService auditService;
    private final Clock clock;

    public AssignmentService(AssignmentRepository assignmentRepository, QuestionRepository questionRepository,
                             AssessmentAssignmentRepository assigneeRepository,
                             AssessmentInvitationRepository invitationRepository,
                             AssessmentAttemptRepository attemptRepository, AssessmentResultRepository resultRepository,
                             AssignmentAttachmentRepository attachmentRepository, UserRepository userRepository,
                             AccessGuard accessGuard, QuestionMapper questionMapper, SettingsService settings,
                             FileStorageService storage, GradingService gradingService, AuditService auditService,
                             Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.questionRepository = questionRepository;
        this.assigneeRepository = assigneeRepository;
        this.invitationRepository = invitationRepository;
        this.attemptRepository = attemptRepository;
        this.resultRepository = resultRepository;
        this.attachmentRepository = attachmentRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
        this.questionMapper = questionMapper;
        this.settings = settings;
        this.storage = storage;
        this.gradingService = gradingService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<AssignmentDto> search(UserPrincipal principal, String search, AssignmentStatus status,
                                              String skill, Pageable pageable) {
        String skillLower = TextUtils.trimToNull(skill) == null ? null : skill.trim().toLowerCase(Locale.ROOT);
        Page<Assignment> page = assignmentRepository.search(accessGuard.ownerScope(principal), status, skillLower,
                TextUtils.likePattern(search), pageable);
        List<Long> ids = page.getContent().stream().map(Assignment::getId).toList();
        Map<Long, long[]> assigneeCounts = new HashMap<>();
        Map<Long, Long> questionCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : assigneeRepository.countsByAssignmentIds(ids)) {
                assigneeCounts.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
            }
            for (Object[] row : assignmentRepository.questionCounts(ids)) {
                questionCounts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }
        return PageResponse.from(page, a -> {
            long[] counts = assigneeCounts.getOrDefault(a.getId(), new long[]{0, 0});
            return toDto(a, questionCounts.getOrDefault(a.getId(), 0L).intValue(), counts[0], counts[1], -1, null, null, principal);
        });
    }

    @Transactional(readOnly = true)
    public AssignmentDto get(Long id, UserPrincipal principal) {
        Assignment a = find(id);
        accessGuard.assertCanManage(principal, a);
        return toDetailDto(a, principal);
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public AssignmentDto create(AssignmentRequest request, UserPrincipal principal) {
        Assignment a = new Assignment();
        a.setOwner(userRepository.getReferenceById(principal.getId()));
        applyFields(a, request, true);
        if (request.questions() != null) {
            setQuestions(a, request.questions(), principal);
        }
        assignmentRepository.save(a);
        auditService.log("ASSIGNMENT_CREATED", "ASSIGNMENT", a.getId(), a.getTitle());
        return toDetailDto(a, principal);
    }

    @Transactional
    public AssignmentDto update(Long id, AssignmentRequest request, UserPrincipal principal) {
        Assignment a = find(id);
        accessGuard.assertCanManage(principal, a);
        if (a.getStatus() == AssignmentStatus.ARCHIVED) {
            throw ApiException.conflict("ASSESSMENT_ARCHIVED", "Archived assessments cannot be edited. Restore it first.");
        }
        boolean hasAttempts = attemptRepository.countByAssignmentId(id) > 0;
        applyFields(a, request, false);
        if (request.questions() != null) {
            if (hasAttempts && questionsDiffer(a, request.questions())) {
                throw ApiException.conflict("ASSESSMENT_HAS_ATTEMPTS",
                        "Questions and marks cannot change after candidates have started. Duplicate the assessment instead.");
            }
            setQuestions(a, request.questions(), principal);
        }
        if (a.getStatus().isOpen()) {
            validatePublishable(a, false);
        }
        auditService.log("ASSIGNMENT_UPDATED", "ASSIGNMENT", a.getId(), a.getTitle());
        return toDetailDto(a, principal);
    }

    @Transactional
    public void delete(Long id, UserPrincipal principal) {
        Assignment a = find(id);
        accessGuard.assertCanManage(principal, a);
        if (attemptRepository.countByAssignmentId(id) > 0) {
            throw ApiException.conflict("ASSESSMENT_HAS_ATTEMPTS",
                    "This assessment has candidate attempts and cannot be deleted. Archive it instead.");
        }
        for (AssignmentAttachment att : attachmentRepository.findByAssignmentIdOrderByCreatedAtAsc(id)) {
            storage.deleteQuietly(ATTACHMENT_AREA, att.getStoredName());
            attachmentRepository.delete(att);
        }
        invitationRepository.deleteByAssignmentId(id);
        assigneeRepository.deleteByAssignmentId(id);
        assignmentRepository.delete(a);
        auditService.log("ASSIGNMENT_DELETED", "ASSIGNMENT", id, a.getTitle());
    }

    @Transactional
    public AssignmentDto publish(Long id, UserPrincipal principal) {
        Assignment a = find(id);
        accessGuard.assertCanManage(principal, a);
        if (a.getStatus() != AssignmentStatus.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS", "Only draft assessments can be published");
        }
        validatePublishable(a, true);
        Instant now = clock.instant();
        a.setStatus(a.getStartAt() == null || !a.getStartAt().isAfter(now) ? AssignmentStatus.ACTIVE : AssignmentStatus.PUBLISHED);
        a.setPublishedAt(now);
        auditService.log("ASSIGNMENT_PUBLISHED", "ASSIGNMENT", a.getId(), a.getTitle() + " -> " + a.getStatus());
        return toDetailDto(a, principal);
    }

    @Transactional
    public AssignmentDto changeStatus(Long id, AssignmentStatus target, UserPrincipal principal) {
        Assignment a = find(id);
        accessGuard.assertCanManage(principal, a);
        AssignmentStatus current = a.getStatus();
        Instant now = clock.instant();
        switch (target) {
            case COMPLETED -> {
                if (!current.isOpen()) {
                    throw invalidTransition(current, target);
                }
                if (attemptRepository.countByAssignmentIdAndStatus(id, AttemptStatus.IN_PROGRESS) > 0) {
                    throw ApiException.conflict("ATTEMPTS_IN_PROGRESS",
                            "Candidates are still taking this assessment. Wait for them to finish or for time to expire.");
                }
            }
            case ARCHIVED -> {
                if (current == AssignmentStatus.ARCHIVED) {
                    throw invalidTransition(current, target);
                }
                if (attemptRepository.countByAssignmentIdAndStatus(id, AttemptStatus.IN_PROGRESS) > 0) {
                    throw ApiException.conflict("ATTEMPTS_IN_PROGRESS", "Candidates are still taking this assessment");
                }
            }
            case ACTIVE -> {
                if (current != AssignmentStatus.COMPLETED) {
                    throw invalidTransition(current, target);
                }
                if (a.getEndAt() != null && !a.getEndAt().isAfter(now)) {
                    throw ApiException.badRequest("END_IN_PAST", "Extend the end date before reopening the assessment");
                }
            }
            case DRAFT -> {
                boolean fromOpen = current.isOpen() && assigneeRepository.countByAssignmentId(id) == 0;
                boolean fromArchive = current == AssignmentStatus.ARCHIVED && attemptRepository.countByAssignmentId(id) == 0;
                if (!fromOpen && !fromArchive) {
                    throw ApiException.conflict("INVALID_STATUS",
                            "Only assessments without candidates can return to draft");
                }
                a.setPublishedAt(null);
            }
            default -> throw ApiException.badRequest("INVALID_STATUS", "Use the publish endpoint to publish an assessment");
        }
        a.setStatus(target);
        auditService.log("ASSIGNMENT_STATUS_CHANGED", "ASSIGNMENT", id, current + " -> " + target);
        return toDetailDto(a, principal);
    }

    @Transactional
    public AssignmentDto duplicate(Long id, UserPrincipal principal) {
        Assignment src = find(id);
        accessGuard.assertCanManage(principal, src);
        Assignment copy = new Assignment();
        copy.setOwner(userRepository.getReferenceById(principal.getId()));
        copy.setTitle(TextUtils.truncate(src.getTitle() + " (copy)", 200));
        copy.setDescription(src.getDescription());
        copy.setInstructions(src.getInstructions());
        copy.setEvaluationCriteria(src.getEvaluationCriteria());
        copy.setSkillCategory(src.getSkillCategory());
        copy.setDifficulty(src.getDifficulty());
        copy.setAssessmentType(src.getAssessmentType());
        copy.setPassingMarks(src.getPassingMarks());
        copy.setDurationMinutes(src.getDurationMinutes());
        copy.setMaxAttempts(src.getMaxAttempts());
        copy.setGracePeriodSeconds(src.getGracePeriodSeconds());
        copy.setShuffleQuestions(src.isShuffleQuestions());
        copy.setShuffleOptions(src.isShuffleOptions());
        copy.setResultsReleasePolicy(src.getResultsReleasePolicy());
        copy.setShowCorrectAnswers(src.isShowCorrectAnswers());
        copy.setMonitoringEnabled(src.isMonitoringEnabled());
        copy.setWarningThreshold(src.getWarningThreshold());
        copy.setMaxViolations(src.getMaxViolations());
        copy.setViolationAction(src.getViolationAction());
        for (AssignmentQuestion aq : src.getQuestions()) {
            AssignmentQuestion n = new AssignmentQuestion();
            n.setAssignment(copy);
            n.setQuestion(aq.getQuestion());
            n.setDisplayOrder(aq.getDisplayOrder());
            n.setMarks(aq.getMarks());
            copy.getQuestions().add(n);
        }
        copy.recalculateTotalMarks();
        assignmentRepository.save(copy);
        auditService.log("ASSIGNMENT_DUPLICATED", "ASSIGNMENT", copy.getId(), "From assessment " + id);
        return toDetailDto(copy, principal);
    }

    @Transactional
    public String releaseResults(Long id, UserPrincipal principal) {
        Assignment a = find(id);
        accessGuard.assertCanManage(principal, a);
        Instant now = clock.instant();
        a.setResultsReleased(true);
        a.setResultsReleasedAt(now);
        int released = 0;
        int pending = 0;
        for (AssessmentResult r : resultRepository.findByAssignmentIdWithUser(id)) {
            if (gradingService.release(r, now)) {
                released++;
            } else if (!r.isReleased()) {
                pending++;
            }
        }
        auditService.log("RESULTS_RELEASED", "ASSIGNMENT", id, released + " released, " + pending + " awaiting grading");
        return released + " result(s) released." + (pending > 0
                ? " " + pending + " result(s) still need grading and will be released automatically once graded." : "");
    }

    // ------------------------------------------------------------------ attachments

    @Transactional
    public AttachmentDto uploadAttachment(Long id, MultipartFile file, UserPrincipal principal) {
        Assignment a = find(id);
        accessGuard.assertCanManage(principal, a);
        FileStorageService.StoredFile stored = storage.store(file, ATTACHMENT_AREA, null, MAX_ATTACHMENT_BYTES);
        AssignmentAttachment att = new AssignmentAttachment();
        att.setAssignment(a);
        att.setOriginalName(stored.originalName());
        att.setStoredName(stored.storedName());
        att.setContentType(stored.contentType());
        att.setSizeBytes(stored.size());
        att.setUploadedBy(userRepository.getReferenceById(principal.getId()));
        attachmentRepository.save(att);
        auditService.log("ATTACHMENT_UPLOADED", "ASSIGNMENT", id, stored.originalName());
        return toAttachmentDto(att);
    }

    @Transactional
    public void deleteAttachment(Long id, Long attachmentId, UserPrincipal principal) {
        Assignment a = find(id);
        accessGuard.assertCanManage(principal, a);
        AssignmentAttachment att = attachmentRepository.findByIdAndAssignmentId(attachmentId, id)
                .orElseThrow(() -> ApiException.notFound("Attachment"));
        storage.deleteQuietly(ATTACHMENT_AREA, att.getStoredName());
        attachmentRepository.delete(att);
        auditService.log("ATTACHMENT_DELETED", "ASSIGNMENT", id, att.getOriginalName());
    }

    public record AttachmentDownload(Resource resource, String fileName) {
    }

    /** Staff who manage the assessment, or candidates assigned to a published assessment. */
    @Transactional(readOnly = true)
    public AttachmentDownload downloadAttachment(Long id, Long attachmentId, UserPrincipal principal) {
        Assignment a = find(id);
        boolean allowed = accessGuard.canManage(principal, a);
        if (!allowed && a.getStatus() != AssignmentStatus.DRAFT && a.getStatus() != AssignmentStatus.ARCHIVED) {
            allowed = assigneeRepository.findByAssignmentIdAndUserId(id, principal.getId())
                    .filter(aa -> aa.getStatus() != AssigneeStatus.REVOKED).isPresent();
        }
        if (!allowed) {
            throw ApiException.notFound("Attachment");
        }
        AssignmentAttachment att = attachmentRepository.findByIdAndAssignmentId(attachmentId, id)
                .orElseThrow(() -> ApiException.notFound("Attachment"));
        return new AttachmentDownload(storage.load(ATTACHMENT_AREA, att.getStoredName()), att.getOriginalName());
    }

    public List<AttachmentDto> attachmentsOf(Long assignmentId) {
        return attachmentRepository.findByAssignmentIdOrderByCreatedAtAsc(assignmentId).stream()
                .map(AssignmentService::toAttachmentDto).toList();
    }

    // ------------------------------------------------------------------ helpers

    Assignment find(Long id) {
        return assignmentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Assessment"));
    }

    private void applyFields(Assignment a, AssignmentRequest r, boolean isNew) {
        if (r.startAt() != null && r.endAt() != null && !r.endAt().isAfter(r.startAt())) {
            throw ApiException.badRequest("INVALID_SCHEDULE", "The end date must be after the start date");
        }
        a.setTitle(r.title().trim());
        a.setDescription(TextUtils.trimToNull(r.description()));
        a.setInstructions(TextUtils.trimToNull(r.instructions()));
        a.setEvaluationCriteria(TextUtils.trimToNull(r.evaluationCriteria()));
        a.setSkillCategory(TextUtils.trimToNull(r.skillCategory()));
        a.setDifficulty(r.difficulty());
        a.setAssessmentType(r.assessmentType());
        a.setPassingMarks(r.passingMarks().setScale(2, RoundingMode.HALF_UP));
        a.setStartAt(r.startAt());
        a.setEndAt(r.endAt());
        a.setDurationMinutes(r.durationMinutes());
        a.setMaxAttempts(r.maxAttempts());
        a.setShuffleQuestions(r.shuffleQuestions());
        a.setShuffleOptions(r.shuffleOptions());
        a.setShowCorrectAnswers(r.showCorrectAnswers());
        if (r.gracePeriodSeconds() != null) {
            a.setGracePeriodSeconds(r.gracePeriodSeconds());
        } else if (isNew) {
            a.setGracePeriodSeconds(settings.getInt(SettingsService.DEFAULT_GRACE_SECONDS));
        }
        if (r.resultsReleasePolicy() != null) {
            a.setResultsReleasePolicy(r.resultsReleasePolicy());
        } else if (isNew) {
            a.setResultsReleasePolicy(settings.getEnum(SettingsService.DEFAULT_RELEASE_POLICY, ReleasePolicy.class));
        }
        if (r.monitoringEnabled() != null) {
            a.setMonitoringEnabled(r.monitoringEnabled());
        }
        if (r.warningThreshold() != null) {
            a.setWarningThreshold(r.warningThreshold());
        } else if (isNew) {
            a.setWarningThreshold(settings.getInt(SettingsService.DEFAULT_WARNING_THRESHOLD));
        }
        if (r.maxViolations() != null) {
            a.setMaxViolations(r.maxViolations());
        } else if (isNew) {
            a.setMaxViolations(settings.getInt(SettingsService.DEFAULT_MAX_VIOLATIONS));
        }
        if (r.violationAction() != null) {
            a.setViolationAction(r.violationAction());
        } else if (isNew) {
            a.setViolationAction(settings.getEnum(SettingsService.DEFAULT_VIOLATION_ACTION, ViolationAction.class));
        }
        if (a.getWarningThreshold() > a.getMaxViolations()) {
            throw ApiException.badRequest("INVALID_MONITORING",
                    "The warning threshold cannot be greater than the maximum number of violations");
        }
    }

    private void setQuestions(Assignment a, List<AssignmentQuestionRequest> items, UserPrincipal principal) {
        Set<Long> requested = new HashSet<>();
        for (AssignmentQuestionRequest item : items) {
            if (!requested.add(item.questionId())) {
                throw ApiException.badRequest("DUPLICATE_QUESTION", "A question can only be added once");
            }
        }
        Map<Long, AssignmentQuestion> existing = new LinkedHashMap<>();
        a.getQuestions().forEach(aq -> existing.put(aq.getQuestion().getId(), aq));
        a.getQuestions().removeIf(aq -> !requested.contains(aq.getQuestion().getId()));

        List<AssignmentQuestion> ordered = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            AssignmentQuestionRequest item = items.get(i);
            AssignmentQuestion aq = existing.get(item.questionId());
            Question q;
            if (aq == null) {
                q = questionRepository.findById(item.questionId())
                        .orElseThrow(() -> ApiException.badRequest("INVALID_QUESTION", "Question " + item.questionId() + " not found"));
                if (!accessGuard.canView(principal, q)) {
                    throw ApiException.badRequest("INVALID_QUESTION", "Question " + item.questionId() + " not found");
                }
                if (q.isArchived()) {
                    throw ApiException.badRequest("QUESTION_ARCHIVED", "Archived question cannot be added: " + q.getTitle());
                }
                aq = new AssignmentQuestion();
                aq.setAssignment(a);
                aq.setQuestion(q);
                a.getQuestions().add(aq);
            } else {
                q = aq.getQuestion();
            }
            aq.setDisplayOrder(i);
            aq.setMarks(item.marks() != null ? item.marks().setScale(2, RoundingMode.HALF_UP) : q.getDefaultMarks());
            ordered.add(aq);
        }
        a.getQuestions().sort(Comparator.comparingInt(AssignmentQuestion::getDisplayOrder));
        a.recalculateTotalMarks();
    }

    private static boolean questionsDiffer(Assignment a, List<AssignmentQuestionRequest> items) {
        List<AssignmentQuestion> current = a.getQuestions().stream()
                .sorted(Comparator.comparingInt(AssignmentQuestion::getDisplayOrder)).toList();
        if (current.size() != items.size()) {
            return true;
        }
        for (int i = 0; i < items.size(); i++) {
            AssignmentQuestion aq = current.get(i);
            AssignmentQuestionRequest item = items.get(i);
            BigDecimal marks = item.marks() == null ? aq.getQuestion().getDefaultMarks() : item.marks();
            if (!Objects.equals(aq.getQuestion().getId(), item.questionId()) || aq.getMarks().compareTo(marks) != 0) {
                return true;
            }
        }
        return false;
    }

    private void validatePublishable(Assignment a, boolean publishing) {
        if (a.getQuestions().isEmpty()) {
            throw ApiException.badRequest("NO_QUESTIONS", "Add at least one question before publishing");
        }
        if (a.getPassingMarks().compareTo(a.getTotalMarks()) > 0) {
            throw ApiException.badRequest("PASSING_EXCEEDS_TOTAL",
                    "Passing marks (" + a.getPassingMarks() + ") cannot exceed total marks (" + a.getTotalMarks() + ")");
        }
        if (publishing && a.getEndAt() != null && !a.getEndAt().isAfter(clock.instant())) {
            throw ApiException.badRequest("END_IN_PAST", "The end date must be in the future");
        }
    }

    private static ApiException invalidTransition(AssignmentStatus from, AssignmentStatus to) {
        return ApiException.conflict("INVALID_STATUS", "Cannot change status from " + from + " to " + to);
    }

    private AssignmentDto toDetailDto(Assignment a, UserPrincipal principal) {
        List<AssignmentQuestionDto> questions = a.getQuestions().stream()
                .sorted(Comparator.comparingInt(AssignmentQuestion::getDisplayOrder))
                .map(aq -> new AssignmentQuestionDto(aq.getId(), aq.getQuestion().getId(), aq.getDisplayOrder(),
                        aq.getMarks(), questionMapper.toStaffDto(aq.getQuestion(), principal)))
                .toList();
        Long id = a.getId();
        long assignees = id == null ? 0 : assigneeRepository.countByAssignmentId(id);
        long completed = id == null ? 0 : assigneeRepository.countByAssignmentIdAndStatus(id, AssigneeStatus.COMPLETED);
        long attempts = id == null ? 0 : attemptRepository.countByAssignmentId(id);
        return toDto(a, questions.size(), assignees, completed, attempts, questions,
                id == null ? List.of() : attachmentsOf(id), principal);
    }

    private AssignmentDto toDto(Assignment a, int questionCount, long assignees, long completed, long attempts,
                                List<AssignmentQuestionDto> questions, List<AttachmentDto> attachments,
                                UserPrincipal principal) {
        return new AssignmentDto(a.getId(), a.getTitle(), a.getDescription(), a.getInstructions(),
                a.getEvaluationCriteria(), a.getSkillCategory(), a.getDifficulty(), a.getAssessmentType(),
                a.getTotalMarks(), a.getPassingMarks(), a.getStartAt(), a.getEndAt(), a.getDurationMinutes(),
                a.getMaxAttempts(), a.getGracePeriodSeconds(), a.isShuffleQuestions(), a.isShuffleOptions(),
                a.getStatus(), a.getResultsReleasePolicy(), a.isShowCorrectAnswers(), a.isResultsReleased(),
                a.getResultsReleasedAt(), a.isMonitoringEnabled(), a.getWarningThreshold(), a.getMaxViolations(),
                a.getViolationAction(), a.getPublishedAt(), a.getOwner().getId(), a.getOwner().getFullName(),
                questionCount, assignees, completed, Math.max(attempts, 0), attempts > 0, questions, attachments,
                a.getCreatedAt(), a.getUpdatedAt());
    }

    static AttachmentDto toAttachmentDto(AssignmentAttachment att) {
        return new AttachmentDto(att.getId(), att.getOriginalName(), att.getContentType(), att.getSizeBytes(), att.getCreatedAt());
    }
}
