package com.assesshub.web;

import com.assesshub.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: author questions, build and publish an assessment, invite by e-mail, accept the
 * secure link, take the timed attempt with autosave, submit, grade manually and view results.
 */
class AssessmentFlowIT extends IntegrationTestBase {

    @Test
    void completeAssessmentLifecycle() throws Exception {
        String admin = adminToken();
        long mcq = singleChoiceQuestion(admin, 2);
        long multi = createQuestion(admin, Map.of("type", "MCQ_MULTI", "title", "Pick evens", "body", "Select even numbers",
                "difficulty", "BEGINNER", "defaultMarks", 3, "options", List.of(
                        Map.of("text", "2", "correct", true), Map.of("text", "3", "correct", false),
                        Map.of("text", "4", "correct", true))));
        long tf = createQuestion(admin, Map.of("type", "TRUE_FALSE", "title", "Sky", "body", "The sky is blue",
                "difficulty", "BEGINNER", "defaultMarks", 1, "options", List.of(
                        Map.of("text", "True", "correct", true), Map.of("text", "False", "correct", false))));
        long shortQ = createQuestion(admin, Map.of("type", "SHORT_ANSWER", "title", "Status", "body", "Not found code?",
                "difficulty", "BEGINNER", "defaultMarks", 1, "acceptedAnswers", List.of("404")));
        long essay = longAnswerQuestion(admin, 5);
        long code = createQuestion(admin, Map.of("type", "CODING", "title", "Sum", "body", "Implement sum(a,b)",
                "difficulty", "BEGINNER", "defaultMarks", 4, "codingLanguage", "javascript",
                "starterCode", "function sum(a, b) {}", "referenceAnswer", "return a + b;"));

        long assignmentId = createAssignment(admin, List.of(mcq, multi, tf, shortQ, essay, code),
                Map.of("passingMarks", 8, "shuffleQuestions", true, "shuffleOptions", true));

        // Invitations are only possible once published.
        postJson("/api/assignments/" + assignmentId + "/invite", admin,
                Map.of("invitees", List.of(Map.of("email", uniqueEmail("early")))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_PUBLISHED"));
        publish(admin, assignmentId);
        getJson("/api/assignments/" + assignmentId, admin)
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.totalMarks").value(16.0));

        // Invite: e-mail link -> public info -> accept (signs the candidate in).
        String candidateEmail = uniqueEmail("cand");
        String invitation = invite(admin, assignmentId, candidateEmail);
        getJson("/api/invitations/" + invitation, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.data.requiresLogin").value(false));
        String candidate = accept(invitation);

        getJson("/api/assessments/my", candidate)
                .andExpect(jsonPath("$.data[0].assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.data[0].state").value("AVAILABLE"));
        getJson("/api/assessments/" + assignmentId, candidate).andExpect(jsonPath("$.data.canStart").value(true));

        // Start: server sets timing; answer key is never exposed.
        MvcResult started = postJson("/api/assessments/" + assignmentId + "/start", candidate, null)
                .andExpect(status().isOk()).andReturn();
        String raw = started.getResponse().getContentAsString();
        assertThat(raw).doesNotContain("\"correct\"").doesNotContain("referenceAnswer").doesNotContain("acceptedAnswers")
                .doesNotContain("return a + b");
        JsonNode view = data(started);
        long attemptId = view.get("attemptId").asLong();
        assertThat(view.get("remainingSeconds").asLong()).isBetween(1790L, 1800L);
        assertThat(view.get("questions")).hasSize(6);

        // Build answers: correct MCQ/TF/short, wrong multi, essay + code for manual grading.
        List<Map<String, Object>> answers = new ArrayList<>();
        for (JsonNode q : view.get("questions")) {
            long qid = q.get("questionId").asLong();
            String type = q.get("type").asText();
            switch (type) {
                case "MCQ_SINGLE" -> answers.add(Map.of("questionId", qid, "selectedOptionIds",
                        List.of(optionId(q, "B"))));
                case "MCQ_MULTI" -> answers.add(Map.of("questionId", qid, "selectedOptionIds",
                        List.of(optionId(q, "2"))));
                case "TRUE_FALSE" -> answers.add(Map.of("questionId", qid, "selectedOptionIds",
                        List.of(optionId(q, "True"))));
                case "SHORT_ANSWER" -> answers.add(Map.of("questionId", qid, "textAnswer", " 404 "));
                case "LONG_ANSWER" -> answers.add(Map.of("questionId", qid, "textAnswer", "Caching stores results..."));
                case "CODING" -> answers.add(Map.of("questionId", qid, "codeAnswer", "function sum(a,b){return a+b}",
                        "codeLanguage", "javascript"));
                default -> throw new IllegalStateException(type);
            }
        }
        putJson("/api/attempts/" + attemptId + "/answers", candidate, Map.of("answers", answers))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.savedCount").value(6));

        // Refresh / reconnect: answers are restored from the server; resuming does not create a new attempt.
        getJson("/api/attempts/" + attemptId, candidate)
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.answers.length()").value(6));
        postJson("/api/assessments/" + assignmentId + "/start", candidate, null)
                .andExpect(jsonPath("$.data.attemptId").value(attemptId));

        // Monitoring events.
        String now = clock.instant().toString();
        postJson("/api/attempts/" + attemptId + "/activity", candidate, Map.of("events", List.of(
                Map.of("type", "TAB_HIDDEN", "occurredAt", now),
                Map.of("type", "WINDOW_BLUR", "occurredAt", now),
                Map.of("type", "TAB_VISIBLE", "occurredAt", now, "durationMs", 4000))))
                .andExpect(jsonPath("$.data.tabSwitchCount").value(1))
                .andExpect(jsonPath("$.data.violationCount").value(1));

        // Another user cannot read or write this attempt (IDOR protection).
        String otherEmail = uniqueEmail("other");
        String otherToken = accept(invite(admin, assignmentId, otherEmail));
        getJson("/api/attempts/" + attemptId, otherToken).andExpect(status().isNotFound());
        putJson("/api/attempts/" + attemptId + "/answers", otherToken, Map.of("answers", List.of()))
                .andExpect(status().isNotFound());

        // Submit.
        postJson("/api/attempts/" + attemptId + "/submit", candidate, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        postJson("/api/attempts/" + attemptId + "/submit", candidate, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_CLOSED"));
        putJson("/api/attempts/" + attemptId + "/answers", candidate, Map.of("answers", List.of()))
                .andExpect(status().isConflict());

        // Results are hidden while manual grading is pending.
        getJson("/api/attempts/" + attemptId + "/results", candidate)
                .andExpect(jsonPath("$.data.released").value(false))
                .andExpect(jsonPath("$.data.totalScore").doesNotExist());

        // Staff review: auto score = MCQ 2 + TF 1 + short 1 = 4 (multi wrong).
        MvcResult review = getJson("/api/attempts/" + attemptId + "/review", admin).andExpect(status().isOk()).andReturn();
        JsonNode rv = data(review);
        assertThat(rv.at("/result/autoScore").asDouble()).isEqualTo(4.0);
        assertThat(rv.at("/result/gradingStatus").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(rv.get("tabSwitchCount").asInt()).isEqualTo(1);

        List<Map<String, Object>> grades = new ArrayList<>();
        for (JsonNode ans : rv.get("answers")) {
            String type = ans.get("type").asText();
            if (type.equals("LONG_ANSWER")) {
                grades.add(Map.of("answerId", ans.get("answerId").asLong(), "marks", 4, "comment", "Good"));
            } else if (type.equals("CODING")) {
                grades.add(Map.of("answerId", ans.get("answerId").asLong(), "marks", 4));
            }
        }
        // Marks above the maximum are rejected by the server.
        long essayAnswerId = ((Number) grades.get(0).get("answerId")).longValue();
        postJson("/api/attempts/" + attemptId + "/grade", admin,
                Map.of("grades", List.of(Map.of("answerId", essayAnswerId, "marks", 50))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MARKS_EXCEED_MAX"));
        // Candidates cannot grade.
        postJson("/api/attempts/" + attemptId + "/grade", candidate, Map.of("grades", grades))
                .andExpect(status().isForbidden());

        postJson("/api/attempts/" + attemptId + "/grade", admin, Map.of("grades", grades, "feedback", "Well done"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.totalScore").value(12.0))
                .andExpect(jsonPath("$.data.result.gradingStatus").value("GRADED"))
                .andExpect(jsonPath("$.data.result.passed").value(true))
                .andExpect(jsonPath("$.data.result.released").value(true));

        // Candidate now sees the released result (correct answers hidden by default).
        MvcResult result = getJson("/api/attempts/" + attemptId + "/results", candidate)
                .andExpect(jsonPath("$.data.released").value(true))
                .andExpect(jsonPath("$.data.totalScore").value(12.0))
                .andExpect(jsonPath("$.data.feedback").value("Well done"))
                .andReturn();
        for (JsonNode q : data(result).get("questions")) {
            if (q.has("options") && q.get("options").isArray()) {
                for (JsonNode o : q.get("options")) {
                    assertThat(o.get("correct").isNull()).isTrue();
                }
            }
        }

        // Attempt limit (1) is enforced.
        postJson("/api/assessments/" + assignmentId + "/start", candidate, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ATTEMPT_LIMIT_REACHED"));

        // Reports, export and dashboard.
        getJson("/api/assignments/" + assignmentId + "/results", admin)
                .andExpect(jsonPath("$.data.totalElements").value(1));
        MvcResult csv = getJson("/api/assignments/" + assignmentId + "/results/export", admin)
                .andExpect(status().isOk()).andReturn();
        assertThat(csv.getResponse().getContentAsString()).contains(candidateEmail).contains("Tab Switches");
        getJson("/api/dashboard/staff", admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAssignments").isNumber());
        getJson("/api/audit-logs?action=ATTEMPT_GRADED", admin)
                .andExpect(jsonPath("$.data.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void duplicateInvitationsReuseTheAssigneeAndRevokeOldLinks() throws Exception {
        String admin = adminToken();
        long assignmentId = createAssignment(admin, List.of(singleChoiceQuestion(admin, 1)), Map.of());
        publish(admin, assignmentId);
        String email = uniqueEmail("dup");

        String first = invite(admin, assignmentId, email);
        postJson("/api/assignments/" + assignmentId + "/invite", admin, Map.of("invitees", List.of(
                        Map.of("email", email), Map.of("email", email.toUpperCase()))))
                .andExpect(jsonPath("$.data.invited").value(0))
                .andExpect(jsonPath("$.data.reinvited").value(1));
        String second = emails.lastTokenTo(email);

        assertThat(second).isNotEqualTo(first);
        getJson("/api/invitations/" + first, null).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITATION_REVOKED"));
        getJson("/api/invitations/" + second, null).andExpect(status().isOk());
        getJson("/api/assignments/" + assignmentId + "/assignees", admin)
                .andExpect(jsonPath("$.data.totalElements").value(1));
        getJson("/api/invitations/" + "x".repeat(43), null).andExpect(status().isNotFound());
    }

    @Test
    void revokingAnAssigneeBlocksTheLinkAndEndsTheAttempt() throws Exception {
        String admin = adminToken();
        long assignmentId = createAssignment(admin, List.of(singleChoiceQuestion(admin, 1)), Map.of());
        publish(admin, assignmentId);
        String email = uniqueEmail("rev");
        String link = invite(admin, assignmentId, email);
        String candidate = accept(link);
        long attemptId = data(postJson("/api/assessments/" + assignmentId + "/start", candidate, null).andReturn())
                .get("attemptId").asLong();

        long assigneeId = data(getJson("/api/assignments/" + assignmentId + "/assignees", admin).andReturn())
                .at("/content/0/id").asLong();
        postJson("/api/assignments/" + assignmentId + "/assignees/" + assigneeId + "/revoke", admin, null)
                .andExpect(status().isOk());

        getJson("/api/invitations/" + link, null).andExpect(status().isGone());
        getJson("/api/attempts/" + attemptId, candidate).andExpect(jsonPath("$.data.status").value("TERMINATED"));
        postJson("/api/assessments/" + assignmentId + "/start", candidate, null).andExpect(status().isForbidden());
    }

    @Test
    void questionsUsedByCandidatesAreLockedAndArchivedInsteadOfDeleted() throws Exception {
        String admin = adminToken();
        long q = singleChoiceQuestion(admin, 1);
        long assignmentId = createAssignment(admin, List.of(q), Map.of());
        publish(admin, assignmentId);
        String candidate = accept(invite(admin, assignmentId, uniqueEmail("lockq")));
        postJson("/api/assessments/" + assignmentId + "/start", candidate, null).andExpect(status().isOk());

        putJson("/api/questions/" + q, admin, Map.of("type", "MCQ_SINGLE", "title", "Changed", "body", "B?",
                "difficulty", "BEGINNER", "defaultMarks", 1, "options", List.of(
                        Map.of("text", "A", "correct", true), Map.of("text", "B", "correct", false))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUESTION_IN_USE"));
        deleteJson("/api/questions/" + q, admin).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("archived")));
        deleteJson("/api/assignments/" + assignmentId, admin).andExpect(status().isConflict());
    }

    private static long optionId(JsonNode question, String text) {
        for (JsonNode o : question.get("options")) {
            if (o.get("text").asText().equals(text)) {
                return o.get("id").asLong();
            }
        }
        throw new IllegalStateException("Option " + text + " not found");
    }
}
