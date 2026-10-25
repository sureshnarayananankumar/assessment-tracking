package com.assesshub.service;

import com.assesshub.domain.AssessmentAssignment;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.AssignmentStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.repository.AssessmentAssignmentRepository;
import com.assesshub.repository.AssessmentAttemptRepository;
import com.assesshub.repository.AssessmentInvitationRepository;
import com.assesshub.repository.AssignmentRepository;
import com.assesshub.repository.PasswordResetTokenRepository;
import com.assesshub.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Background jobs: server-side auto-submission of expired attempts, assessment status
 * transitions, invitation expiry, deadline reminders and token clean-up.
 * <p>
 * When running several backend replicas, add a distributed lock (e.g. ShedLock) - each job is
 * idempotent (row locks + optimistic versions), so concurrent runs are safe but redundant.
 */
@Component
@ConditionalOnProperty(prefix = "app.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final AssessmentAttemptRepository attemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssessmentAssignmentRepository assigneeRepository;
    private final AssessmentInvitationRepository invitationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final AttemptService attemptService;
    private final InvitationService invitationService;
    private final SettingsService settings;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ScheduledTasks(AssessmentAttemptRepository attemptRepository, AssignmentRepository assignmentRepository,
                          AssessmentAssignmentRepository assigneeRepository,
                          AssessmentInvitationRepository invitationRepository,
                          RefreshTokenRepository refreshTokenRepository,
                          PasswordResetTokenRepository resetTokenRepository, AttemptService attemptService,
                          InvitationService invitationService, SettingsService settings, TransactionTemplate tx,
                          Clock clock) {
        this.attemptRepository = attemptRepository;
        this.assignmentRepository = assignmentRepository;
        this.assigneeRepository = assigneeRepository;
        this.invitationRepository = invitationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.attemptService = attemptService;
        this.invitationService = invitationService;
        this.settings = settings;
        this.tx = tx;
        this.clock = clock;
    }

    /** Auto-submits attempts whose deadline (+ grace period) has passed. */
    @Scheduled(fixedDelayString = "${app.scheduler.expiry-check-ms:15000}", initialDelay = 10_000)
    public void autoSubmitExpiredAttempts() {
        List<Long> ids = attemptRepository.findInProgressPastDeadline(clock.instant());
        for (Long id : ids) {
            try {
                attemptService.autoSubmitIfExpired(id);
            } catch (RuntimeException ex) {
                log.warn("Auto-submit failed for attempt {}: {}", id, ex.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.scheduler.maintenance-ms:60000}", initialDelay = 20_000)
    public void maintenance() {
        safely("assessment status", this::updateAssignmentStatuses);
        safely("assignee expiry", this::expireAssignees);
        safely("invitation expiry", () -> tx.executeWithoutResult(s -> invitationRepository.expireOverdue(clock.instant())));
        safely("reminders", this::sendReminders);
        safely("token clean-up", () -> tx.executeWithoutResult(s -> {
            Instant cutoff = clock.instant().minus(Duration.ofDays(30));
            refreshTokenRepository.deleteExpiredBefore(cutoff);
            resetTokenRepository.deleteExpiredBefore(cutoff);
        }));
    }

    void updateAssignmentStatuses() {
        tx.executeWithoutResult(s -> {
            Instant now = clock.instant();
            for (Assignment a : assignmentRepository.findReadyToActivate(now)) {
                a.setStatus(AssignmentStatus.ACTIVE);
                log.info("Assessment {} is now ACTIVE", a.getId());
            }
            for (Assignment a : assignmentRepository.findOpenPastEnd(now)) {
                boolean busy = attemptRepository.countByAssignmentIdAndStatus(a.getId(), AttemptStatus.IN_PROGRESS) > 0;
                boolean extended = assigneeRepository.hasExtendedDeadlines(a.getId(), now);
                if (!busy && !extended) {
                    a.setStatus(AssignmentStatus.COMPLETED);
                    log.info("Assessment {} window closed -> COMPLETED", a.getId());
                }
            }
        });
    }

    void expireAssignees() {
        tx.executeWithoutResult(s -> {
            for (AssessmentAssignment aa : assigneeRepository.findAssignedPastDeadline(clock.instant())) {
                aa.setStatus(aa.getAttemptsUsed() > 0 ? AssigneeStatus.COMPLETED : AssigneeStatus.EXPIRED);
            }
        });
    }

    void sendReminders() {
        if (!settings.getBoolean(SettingsService.REMINDERS_ENABLED)) {
            return;
        }
        Instant now = clock.instant();
        Instant until = now.plus(Duration.ofHours(settings.getInt(SettingsService.REMINDER_HOURS_BEFORE)));
        List<Long> ids = tx.execute(s -> assigneeRepository.findDueForReminder(now, until).stream()
                .map(AssessmentAssignment::getId).toList());
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(s -> assigneeRepository.findById(id)
                        .ifPresent(aa -> invitationService.sendReminder(aa, null)));
            } catch (RuntimeException ex) {
                log.warn("Reminder failed for assignee {}: {}", id, ex.getMessage());
            }
        }
    }

    private void safely(String name, Runnable job) {
        try {
            job.run();
        } catch (RuntimeException ex) {
            log.warn("Scheduled job '{}' failed: {}", name, ex.getMessage());
        }
    }
}
