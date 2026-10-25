package com.assesshub.web;

import com.assesshub.domain.enums.RoleName;
import com.assesshub.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthAndAuthorizationIT extends IntegrationTestBase {

    @Test
    void loginIssuesAccessTokenAndHttpOnlyStrictRefreshCookie() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.roles[0]").value("SUPER_ADMIN"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("ah_refresh="),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Strict"))));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        postJson("/api/auth/login", null, Map.of("email", ADMIN_EMAIL, "password", "wrong-pass1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        postJson("/api/auth/login", null, Map.of("email", "nobody@test.local", "password", "wrong-pass1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void accountIsLockedAfterRepeatedFailures() throws Exception {
        String email = uniqueEmail("lock");
        createUser(email, RoleName.RECRUITER);
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/login", null, Map.of("email", email, "password", "bad-password1"))
                    .andExpect(status().isUnauthorized());
        }
        postJson("/api/auth/login", null, Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        getJson("/api/auth/me", null).andExpect(status().isUnauthorized());
        getJson("/api/auth/me", "not-a-jwt").andExpect(status().isUnauthorized());
        getJson("/api/auth/me", adminToken()).andExpect(status().isOk()).andExpect(jsonPath("$.data.email").value(ADMIN_EMAIL));
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() throws Exception {
        MvcResult login = postJson("/api/auth/login", null, Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD))
                .andExpect(status().isOk()).andReturn();
        Cookie original = login.getResponse().getCookie("ah_refresh");
        assertThat(original).isNotNull();

        // Missing CSRF-defence header is refused.
        mvc.perform(post("/api/auth/refresh").cookie(original)).andExpect(status().isForbidden());

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(original).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty()).andReturn();
        Cookie rotated = refreshed.getResponse().getCookie("ah_refresh");
        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());

        // Re-using the old token revokes the whole family.
        mvc.perform(post("/api/auth/refresh").cookie(original).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(rotated).header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetFlowWorksAndTokensAreSingleUse() throws Exception {
        String email = uniqueEmail("reset");
        createUser(email, RoleName.CANDIDATE);
        postJson("/api/auth/forgot-password", null, Map.of("email", "unknown@test.local")).andExpect(status().isOk());
        postJson("/api/auth/forgot-password", null, Map.of("email", email)).andExpect(status().isOk());
        String token = emails.lastTokenTo(email);

        getJson("/api/auth/reset-password/validate?token=" + token, null)
                .andExpect(jsonPath("$.data.valid").value(true));
        postJson("/api/auth/reset-password", null, Map.of("token", token, "newPassword", "weak"))
                .andExpect(status().isBadRequest());
        postJson("/api/auth/reset-password", null, Map.of("token", token, "newPassword", "BrandNew123"))
                .andExpect(status().isOk());
        postJson("/api/auth/reset-password", null, Map.of("token", token, "newPassword", "Another123"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        login(email, "BrandNew123");
    }

    @Test
    void candidatesCannotReachStaffEndpoints() throws Exception {
        String email = uniqueEmail("cand");
        createUser(email, RoleName.CANDIDATE);
        String token = login(email, PASSWORD);
        getJson("/api/users", token).andExpect(status().isForbidden());
        getJson("/api/assignments", token).andExpect(status().isForbidden());
        getJson("/api/questions", token).andExpect(status().isForbidden());
        getJson("/api/audit-logs", token).andExpect(status().isForbidden());
        getJson("/api/dashboard/staff", token).andExpect(status().isForbidden());
        getJson("/api/assessments/my", token).andExpect(status().isOk());
    }

    @Test
    void recruitersOnlySeeTheirOwnAssessments() throws Exception {
        String a = uniqueEmail("rec-a");
        String b = uniqueEmail("rec-b");
        createUser(a, RoleName.RECRUITER);
        createUser(b, RoleName.RECRUITER);
        String tokenA = login(a, PASSWORD);
        String tokenB = login(b, PASSWORD);
        long q = singleChoiceQuestion(tokenA, 1);
        long assignmentId = createAssignment(tokenA, List.of(q), Map.of());

        getJson("/api/assignments/" + assignmentId, tokenB).andExpect(status().isNotFound());
        putJson("/api/assignments/" + assignmentId, tokenB, Map.of("title", "x", "difficulty", "BEGINNER",
                "assessmentType", "QUIZ", "passingMarks", 0, "durationMinutes", 5, "maxAttempts", 1))
                .andExpect(status().isNotFound());
        getJson("/api/questions/" + q, tokenB).andExpect(status().isNotFound());
        getJson("/api/assignments/" + assignmentId, tokenA).andExpect(status().isOk());
        getJson("/api/assignments/" + assignmentId, adminToken()).andExpect(status().isOk());
    }

    @Test
    void onlySuperAdminsCanGrantAdministrativeRoles() throws Exception {
        String adminEmail = uniqueEmail("adm");
        createUser(adminEmail, RoleName.ADMIN);
        String adminToken = login(adminEmail, PASSWORD);
        postJson("/api/users", adminToken, Map.of("email", uniqueEmail("x"), "fullName", "X", "roles", List.of("SUPER_ADMIN")))
                .andExpect(status().isForbidden());
        postJson("/api/users", adminToken, Map.of("email", uniqueEmail("r"), "fullName", "R", "roles", List.of("RECRUITER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("INVITED"));
        postJson("/api/users", adminToken(), Map.of("email", uniqueEmail("s"), "fullName", "S", "roles", List.of("ADMIN")))
                .andExpect(status().isCreated());
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        postJson("/api/questions", adminToken(), Map.of("type", "MCQ_SINGLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }
}
