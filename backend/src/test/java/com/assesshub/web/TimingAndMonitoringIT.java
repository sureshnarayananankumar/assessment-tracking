package com.assesshub.web;

import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.repository.AssessmentAttemptRepository;
import com.assesshub.repository.AssessmentResultRepository;
import com.assesshub.service.AttemptService;
import com.assesshub.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Server-enforced timing (deadline, grace period, automatic submission) and focus-event logging.
 */
class TimingAndMonitoringIT extends IntegrationTestBase {

    @Autowired
    AttemptService attemptService;
    @Autowired
    AssessmentAttemptRepository attemptRepository;
    @Autowired
    AssessmentResultRepository resultRepository;

    private record Started(String candidate, long attemptId, long questionId) {
    }

    private Started startShortAttempt(Map<String, Object> overrides) throws Exception {
        String admin = adminToken();
        long q = singleChoiceQuestion(admin, 2);
        Map<String, Object> settings = new java.util.HashMap<>(Map.of("durationMinutes", 1, "gracePeriodSeconds", 5));
        settings.putAll(overrides);
        long assignmentId = createAssignment(admin, List.of(q), settings);
        publish(admin, assignmentId);
        String candidate = accept(invite(admin, assignmentId, uniqueEmail("timer")));
        JsonNode view = data(postJson("/api/assessments/" + assignmentId + "/start", candidate, null)
                .andExpect(status().isOk()).andReturn());
        return new Started(candidate, view.get("attemptId").asLong(), q);
    }

    @Test
    void answersAreRejectedAfterDeadlineAndTheAttemptIsAutoSubmitted() throws Exception {
        Started s = startShortAttempt(Map.of());

        clock.advance(Duration.ofSeconds(62)); // past deadline but inside the 5s grace period
        putJson("/api/attempts/" + s.attemptId() + "/answers", s.candidate(),
                Map.of("answers", List.of(Map.of("questionId", s.questionId(), "selectedOptionIds", List.of()))))
                .andExpect(status().isOk());

        clock.advance(Duration.ofSeconds(10)); // grace period over
        putJson("/api/attempts/" + s.attemptId() + "/answers", s.candidate(),
                Map.of("answers", List.of(Map.of("questionId", s.questionId(), "selectedOptionIds", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));

        getJson("/api/attempts/" + s.attemptId(), s.candidate())
                .andExpect(jsonPath("$.data.status").value("AUTO_SUBMITTED"))
                .andExpect(jsonPath("$.data.remainingSeconds").value(0))
                .andExpect(jsonPath("$.data.questions.length()").value(0));
        assertThat(resultRepository.findByAttemptId(s.attemptId())).isPresent();
    }

    @Test
    void lateSubmitIsRejected() throws Exception {
        Started s = startShortAttempt(Map.of());
        clock.advance(Duration.ofSeconds(120));
        postJson("/api/attempts/" + s.attemptId() + "/submit", s.candidate(), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBMISSION_LATE"));
        assertThat(attemptRepository.findById(s.attemptId()).orElseThrow().getStatus()).isEqualTo(AttemptStatus.AUTO_SUBMITTED);
    }

    @Test
    void schedulerJobAutoSubmitsExpiredAttempts() throws Exception {
        Started s = startShortAttempt(Map.of());
        assertThat(attemptService.autoSubmitIfExpired(s.attemptId())).isFalse();
        clock.advance(Duration.ofSeconds(70));
        assertThat(attemptService.autoSubmitIfExpired(s.attemptId())).isTrue();
        assertThat(attemptRepository.findById(s.attemptId()).orElseThrow().getStatus()).isEqualTo(AttemptStatus.AUTO_SUBMITTED);
        assertThat(attemptService.autoSubmitIfExpired(s.attemptId())).isFalse(); // idempotent
    }

    @Test
    void focusEventsAreLoggedCountedAndCanTerminateTheAttempt() throws Exception {
        Started s = startShortAttempt(Map.of("durationMinutes", 30, "warningThreshold", 1, "maxViolations", 2,
                "violationAction", "AUTO_SUBMIT"));
        String t1 = clock.instant().toString();

        postJson("/api/attempts/" + s.attemptId() + "/activity", s.candidate(), Map.of("events", List.of(
                Map.of("type", "TAB_HIDDEN", "occurredAt", t1),
                Map.of("type", "WINDOW_BLUR", "occurredAt", t1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.violationCount").value(1))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.newViolation").value(true));

        clock.advance(Duration.ofSeconds(30));
        postJson("/api/attempts/" + s.attemptId() + "/activity", s.candidate(), Map.of("events", List.of(
                Map.of("type", "TAB_HIDDEN", "occurredAt", clock.instant().toString()))))
                .andExpect(jsonPath("$.data.violationCount").value(2))
                .andExpect(jsonPath("$.data.status").value("TERMINATED"));

        String admin = adminToken();
        JsonNode log = data(getJson("/api/attempts/" + s.attemptId() + "/activity", admin).andReturn());
        List<String> types = new java.util.ArrayList<>();
        log.forEach(e -> types.add(e.get("eventType").asText()));
        assertThat(types).contains("STARTED", "TAB_HIDDEN", "WINDOW_BLUR", "TERMINATED");

        getJson("/api/monitoring/attempts?flagged=true", admin)
                .andExpect(jsonPath("$.data.content[?(@.attemptId == " + s.attemptId() + ")]").exists());
        getJson("/api/attempts/" + s.attemptId() + "/review", admin)
                .andExpect(jsonPath("$.data.result.flagged").value(true))
                .andExpect(jsonPath("$.data.result.tabSwitchCount").value(2));
    }

    @Test
    void invalidEventPayloadIsRejected() throws Exception {
        Started s = startShortAttempt(Map.of());
        postJson("/api/attempts/" + s.attemptId() + "/activity", s.candidate(), Map.of("events", List.of()))
                .andExpect(status().isBadRequest());
        postJson("/api/attempts/" + s.attemptId() + "/activity", s.candidate(),
                Map.of("events", List.of(Map.of("type", "NOT_A_TYPE"))))
                .andExpect(status().isBadRequest());
    }
}
