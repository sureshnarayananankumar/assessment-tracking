package com.assesshub.service;

import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.dto.MiscDtos.CountItem;
import com.assesshub.dto.MiscDtos.MonitoringAttemptDto;
import com.assesshub.dto.MiscDtos.StaffDashboardDto;
import com.assesshub.repository.AssessmentAssignmentRepository;
import com.assesshub.repository.AssessmentAttemptRepository;
import com.assesshub.repository.AssessmentResultRepository;
import com.assesshub.repository.AssignmentRepository;
import com.assesshub.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated statistics for administrators (organisation-wide) and recruiters (own assessments only).
 */
@Service
public class DashboardService {

    private final AssignmentRepository assignmentRepository;
    private final AssessmentAssignmentRepository assigneeRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final AssessmentResultRepository resultRepository;
    private final ResultService resultService;
    private final AccessGuard accessGuard;
    private final Clock clock;

    public DashboardService(AssignmentRepository assignmentRepository, AssessmentAssignmentRepository assigneeRepository,
                            AssessmentAttemptRepository attemptRepository, AssessmentResultRepository resultRepository,
                            ResultService resultService, AccessGuard accessGuard, Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.assigneeRepository = assigneeRepository;
        this.attemptRepository = attemptRepository;
        this.resultRepository = resultRepository;
        this.resultService = resultService;
        this.accessGuard = accessGuard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StaffDashboardDto staffDashboard(UserPrincipal principal) {
        Long owner = accessGuard.ownerScope(principal);
        Instant now = clock.instant();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        long totalAssignments = 0;
        for (Object[] row : assignmentRepository.countByStatus(owner)) {
            long c = ((Number) row[1]).longValue();
            byStatus.put(String.valueOf(row[0]), c);
            totalAssignments += c;
        }
        Map<AssigneeStatus, Long> assignees = new LinkedHashMap<>();
        for (Object[] row : assigneeRepository.countByStatus(owner)) {
            assignees.put((AssigneeStatus) row[0], ((Number) row[1]).longValue());
        }
        Object[] stats = resultRepository.stats(owner).get(0);
        BigDecimal avg = toBigDecimal(stats[0]).setScale(1, RoundingMode.HALF_UP);
        Object[] totals = attemptRepository.activityTotals(owner).get(0);

        int[] buckets = new int[5];
        for (BigDecimal p : resultRepository.gradedPercentages(owner)) {
            int idx = Math.min(4, p.intValue() / 20);
            buckets[Math.max(0, idx)]++;
        }
        List<CountItem> distribution = new ArrayList<>();
        String[] labels = {"0-19%", "20-39%", "40-59%", "60-79%", "80-100%"};
        for (int i = 0; i < labels.length; i++) {
            distribution.add(new CountItem(labels[i], buckets[i]));
        }

        List<MonitoringAttemptDto> recent = attemptRepository.search(owner, null, null, null, null,
                        PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "startedAt")))
                .map(t -> resultService.toMonitoringDto(t, now)).getContent();
        List<MonitoringAttemptDto> flagged = attemptRepository.search(owner, null, null, true, null,
                        PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "startedAt")))
                .map(t -> resultService.toMonitoringDto(t, now)).getContent();

        return new StaffDashboardDto(
                totalAssignments,
                byStatus,
                assigneeRepository.countDistinctInvitedUsers(owner),
                assignees.getOrDefault(AssigneeStatus.ASSIGNED, 0L),
                attemptRepository.countByStatusForOwner(AttemptStatus.IN_PROGRESS, owner),
                assignees.getOrDefault(AssigneeStatus.COMPLETED, 0L),
                assignees.getOrDefault(AssigneeStatus.EXPIRED, 0L),
                avg,
                ((Number) stats[1]).longValue(),
                ((Number) stats[2]).longValue(),
                ((Number) stats[3]).longValue(),
                attemptRepository.countFlagged(owner),
                ((Number) totals[0]).longValue(),
                ((Number) totals[1]).longValue(),
                ((Number) totals[2]).longValue(),
                distribution,
                recent,
                flagged);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return BigDecimal.ZERO;
    }
}
