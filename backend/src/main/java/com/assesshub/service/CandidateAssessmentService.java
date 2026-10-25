package com.assesshub.service;

import com.assesshub.common.exception.ApiException;
import com.assesshub.domain.AssessmentAssignment;
import com.assesshub.domain.AssessmentAttempt;
import com.assesshub.domain.AssessmentResult;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.AssignmentStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.CandidateAssessmentState;
import com.assesshub.dto.CandidateDtos.AssessmentInstructionsDto;
import com.assesshub.dto.CandidateDtos.MyAssessmentDto;
import com.assesshub.repository.AssessmentAssignmentRepository;
import com.assesshub.repository.AssessmentAttemptRepository;
import com.assesshub.repository.AssessmentResultRepository;
import com.assesshub.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Candidate-facing listing of assigned assessments. Availability is computed on the server
 * from the schedule, deadline, attempt count and assessment status.
 */
@Service
public class CandidateAssessmentService {

    record StateInfo(CandidateAssessmentState state, boolean canStart, String reason) {
    }

    private final AssessmentAssignmentRepository assigneeRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentResultRepository resultRepository;
    private final AssignmentService assignmentService;
    private final Clock clock;

    public CandidateAssessmentService(AssessmentAssignmentRepository assigneeRepository,
                                      AssessmentAttemptRepository attemptRepository,
                                      AssessmentResultRepository resultRepository,
                                      AssignmentService assignmentService, Clock clock) {
        this.assigneeRepository = assigneeRepository;
        this.attemptRepository = attemptRepository;
        this.resultRepository = resultRepository;
        this.assignmentService = assignmentService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MyAssessmentDto> listMine(UserPrincipal principal) {
        Instant now = clock.instant();
        List<AssessmentAssignment> assignees = assigneeRepository.findVisibleForUser(principal.getId());
        Map<Long, List<AssessmentAttempt>> attemptsByAssignee = attemptRepository.findByUserIdOrderByStartedAtDesc(principal.getId())
                .stream().collect(Collectors.groupingBy(t -> t.getAssessmentAssignment().getId()));
        Map<Long, AssessmentResult> resultsByAttempt = new HashMap<>();
        resultRepository.findByUserId(principal.getId()).forEach(r -> resultsByAttempt.put(r.getAttempt().getId(), r));
        return assignees.stream()
                .map(aa -> toDto(aa, attemptsByAssignee.getOrDefault(aa.getId(), List.of()), resultsByAttempt, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssessmentInstructionsDto instructions(Long assignmentId, UserPrincipal principal) {
        Instant now = clock.instant();
        AssessmentAssignment aa = assigneeRepository.findByAssignmentIdAndUserId(assignmentId, principal.getId())
                .filter(x -> x.getStatus() != AssigneeStatus.REVOKED)
                .orElseThrow(() -> ApiException.notFound("Assessment"));
        Assignment a = aa.getAssignment();
        if (a.getStatus() == AssignmentStatus.DRAFT || a.getStatus() == AssignmentStatus.ARCHIVED) {
            throw ApiException.notFound("Assessment");
        }
        List<AssessmentAttempt> attempts = attemptRepository.findByAssessmentAssignmentIdOrderByAttemptNumberDesc(aa.getId());
        Map<Long, AssessmentResult> results = new HashMap<>();
        attempts.forEach(t -> resultRepository.findByAttemptId(t.getId()).ifPresent(r -> results.put(t.getId(), r)));
        MyAssessmentDto summary = toDto(aa, attempts, results, now);
        StateInfo info = evaluate(aa, attempts, now);
        return new AssessmentInstructionsDto(summary, a.getInstructions(), a.getEvaluationCriteria(), a.getPassingMarks(),
                a.isMonitoringEnabled(), a.getWarningThreshold(), a.getMaxViolations(), a.getViolationAction(),
                a.getGracePeriodSeconds(), assignmentService.attachmentsOf(a.getId()), info.canStart(), info.reason(), now);
    }

    StateInfo evaluate(AssessmentAssignment aa, List<AssessmentAttempt> attempts, Instant now) {
        Assignment a = aa.getAssignment();
        boolean running = attempts.stream().anyMatch(t -> t.getStatus() == AttemptStatus.IN_PROGRESS);
        if (running) {
            return new StateInfo(CandidateAssessmentState.IN_PROGRESS, true, null);
        }
        Instant deadline = aa.effectiveDeadline();
        boolean closed = a.getStatus() == AssignmentStatus.COMPLETED || (deadline != null && !now.isBefore(deadline));
        if (closed) {
            return new StateInfo(aa.getAttemptsUsed() > 0 ? CandidateAssessmentState.COMPLETED : CandidateAssessmentState.EXPIRED,
                    false, "The assessment window has closed");
        }
        if (aa.getAttemptsUsed() >= a.getMaxAttempts()) {
            return new StateInfo(CandidateAssessmentState.COMPLETED, false, "You have used all permitted attempts");
        }
        if (a.getStartAt() != null && now.isBefore(a.getStartAt())) {
            return new StateInfo(CandidateAssessmentState.UPCOMING, false, "This assessment has not opened yet");
        }
        if (!a.getStatus().isOpen()) {
            return new StateInfo(CandidateAssessmentState.EXPIRED, false, "This assessment is not open");
        }
        return new StateInfo(CandidateAssessmentState.AVAILABLE, true, null);
    }

    private MyAssessmentDto toDto(AssessmentAssignment aa, List<AssessmentAttempt> attempts,
                                  Map<Long, AssessmentResult> results, Instant now) {
        Assignment a = aa.getAssignment();
        StateInfo info = evaluate(aa, attempts, now);
        AssessmentAttempt latest = attempts.stream().max(Comparator.comparingInt(AssessmentAttempt::getAttemptNumber)).orElse(null);
        AssessmentAttempt active = attempts.stream().filter(t -> t.getStatus() == AttemptStatus.IN_PROGRESS).findFirst().orElse(null);
        AssessmentResult result = latest == null ? null : results.get(latest.getId());
        boolean released = result != null && result.isReleased();
        return new MyAssessmentDto(a.getId(), aa.getId(), a.getTitle(), a.getDescription(), a.getSkillCategory(),
                a.getDifficulty(), a.getAssessmentType(), a.getDurationMinutes(), a.getStartAt(), aa.effectiveDeadline(),
                a.getMaxAttempts(), aa.getAttemptsUsed(), info.state(), a.getQuestions().size(), a.getTotalMarks(),
                active == null ? null : active.getId(), latest == null ? null : latest.getId(),
                latest == null ? null : latest.getStatus(), latest == null ? null : latest.getSubmittedAt(), released,
                released ? result.getTotalScore() : null, released ? result.getPercentage() : null,
                released ? result.getPassed() : null, aa.getAssignedAt());
    }
}
