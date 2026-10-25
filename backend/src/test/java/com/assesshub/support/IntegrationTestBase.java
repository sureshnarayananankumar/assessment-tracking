package com.assesshub.support;

import com.assesshub.domain.User;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.domain.enums.UserStatus;
import com.assesshub.repository.RoleRepository;
import com.assesshub.repository.UserRepository;
import com.assesshub.service.email.EmailRequestedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the full application (security, JPA on H2 in MySQL mode, services) and drives it over
 * HTTP with MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTestBase.TestBeans.class)
public abstract class IntegrationTestBase {

    public static final String ADMIN_EMAIL = "admin@test.local";
    public static final String ADMIN_PASSWORD = "AdminPass123";
    public static final String PASSWORD = "Password123";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper om;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected RoleRepository roles;
    @Autowired
    protected PasswordEncoder encoder;
    @Autowired
    protected EmailCapture emails;
    @Autowired
    protected MutableClock clock;

    @TestConfiguration
    public static class TestBeans {
        @Bean
        @Primary
        public MutableClock testClock() {
            return new MutableClock(Instant.now());
        }

        @Bean
        public EmailCapture emailCapture() {
            return new EmailCapture();
        }
    }

    /** Captures e-mails synchronously at publish time so tests can follow links. */
    public static class EmailCapture {
        private final List<EmailRequestedEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(EmailRequestedEvent event) {
            events.add(event);
        }

        public String lastLinkTo(String email) {
            for (int i = events.size() - 1; i >= 0; i--) {
                if (events.get(i).to().equalsIgnoreCase(email)) {
                    return events.get(i).link();
                }
            }
            throw new AssertionError("No e-mail captured for " + email);
        }

        public String lastTokenTo(String email) {
            String link = lastLinkTo(email);
            if (link.contains("/invite/")) {
                return link.substring(link.indexOf("/invite/") + 8);
            }
            String token = link.substring(link.indexOf("token=") + 6);
            int amp = token.indexOf('&');
            return amp > 0 ? token.substring(0, amp) : token;
        }
    }

    @BeforeEach
    void resetClock() {
        clock.setInstant(Instant.now());
    }

    // ------------------------------------------------------------------ helpers

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    protected User createUser(String email, RoleName role) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(role.name() + " " + email.substring(0, 6));
        u.setPasswordHash(encoder.encode(PASSWORD));
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        u.setRoles(new HashSet<>(Set.of(roles.findByName(role).orElseThrow())));
        return users.save(u);
    }

    protected String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return data(r).get("accessToken").asText();
    }

    protected String adminToken() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected String json(Object value) throws Exception {
        return om.writeValueAsString(value);
    }

    protected JsonNode body(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString());
    }

    protected JsonNode data(MvcResult r) throws Exception {
        return body(r).get("data");
    }

    protected ResultActions call(MockHttpServletRequestBuilder builder, String token, Object payload) throws Exception {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (payload != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(json(payload));
        }
        return mvc.perform(builder);
    }

    protected ResultActions getJson(String url, String token) throws Exception {
        return call(get(url), token, null);
    }

    protected ResultActions postJson(String url, String token, Object payload) throws Exception {
        return call(post(url), token, payload);
    }

    protected ResultActions putJson(String url, String token, Object payload) throws Exception {
        return call(put(url), token, payload);
    }

    protected ResultActions deleteJson(String url, String token) throws Exception {
        return call(delete(url), token, null);
    }

    // ------------------------------------------------------------------ domain helpers

    protected long createQuestion(String token, Map<String, Object> payload) throws Exception {
        MvcResult r = postJson("/api/questions", token, payload).andExpect(status().isCreated()).andReturn();
        return data(r).get("id").asLong();
    }

    protected long singleChoiceQuestion(String token, int marks) throws Exception {
        return createQuestion(token, Map.of("type", "MCQ_SINGLE", "title", "Pick B", "body", "Which is B?",
                "difficulty", "BEGINNER", "defaultMarks", marks,
                "options", List.of(Map.of("text", "A", "correct", false), Map.of("text", "B", "correct", true))));
    }

    protected long longAnswerQuestion(String token, int marks) throws Exception {
        return createQuestion(token, Map.of("type", "LONG_ANSWER", "title", "Explain", "body", "Explain caching",
                "difficulty", "INTERMEDIATE", "defaultMarks", marks, "evaluationGuidelines", "Clarity"));
    }

    protected long createAssignment(String token, List<Long> questionIds, Map<String, Object> overrides) throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>(Map.of(
                "title", "Assessment " + UUID.randomUUID().toString().substring(0, 6),
                "difficulty", "INTERMEDIATE",
                "assessmentType", "MIXED",
                "passingMarks", 1,
                "durationMinutes", 30,
                "maxAttempts", 1,
                "gracePeriodSeconds", 0,
                "questions", questionIds.stream().map(id -> Map.of("questionId", id)).toList()));
        payload.putAll(overrides);
        MvcResult r = postJson("/api/assignments", token, payload).andExpect(status().isCreated()).andReturn();
        return data(r).get("id").asLong();
    }

    protected void publish(String token, long assignmentId) throws Exception {
        postJson("/api/assignments/" + assignmentId + "/publish", token, null).andExpect(status().isOk());
    }

    /** Invites the e-mail and returns the raw invitation token taken from the captured e-mail. */
    protected String invite(String token, long assignmentId, String email) throws Exception {
        postJson("/api/assignments/" + assignmentId + "/invite", token,
                Map.of("invitees", List.of(Map.of("email", email, "fullName", "Test Candidate"))))
                .andExpect(status().isOk());
        return emails.lastTokenTo(email);
    }

    /** Accepts an invitation and returns the candidate access token. */
    protected String accept(String invitationToken) throws Exception {
        MvcResult r = postJson("/api/invitations/" + invitationToken + "/accept", null, Map.of())
                .andExpect(status().isOk()).andReturn();
        return data(r).at("/auth/accessToken").asText();
    }
}
