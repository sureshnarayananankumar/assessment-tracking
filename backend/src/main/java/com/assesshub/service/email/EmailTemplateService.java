package com.assesshub.service.email;

import com.assesshub.config.AppProperties;
import com.assesshub.domain.Assignment;
import com.assesshub.domain.User;
import com.assesshub.domain.enums.EmailTemplate;
import com.assesshub.service.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Builds HTML + plain-text e-mails. All user-controlled content is HTML-escaped.
 */
@Service
public class EmailTemplateService {

    private static final DateTimeFormatter UTC_FORMAT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private final AppProperties props;
    private final SettingsService settings;

    public EmailTemplateService(AppProperties props, SettingsService settings) {
        this.props = props;
        this.settings = settings;
    }

    public String invitationLink(String token) {
        return props.frontendBaseUrl() + "/invite/" + token;
    }

    public EmailRequestedEvent invitation(User user, Assignment a, Instant deadline, String token,
                                          String customMessage, boolean reminder) {
        String org = settings.orgName();
        String link = invitationLink(token);
        String subject = (reminder ? "Reminder: " : "") + "Assessment invitation - " + a.getTitle();
        String start = a.getStartAt() == null ? "Available now" : UTC_FORMAT.format(a.getStartAt());
        String due = deadline == null ? "No deadline" : UTC_FORMAT.format(deadline);
        String duration = formatDuration(a.getDurationMinutes());

        StringBuilder rows = new StringBuilder()
                .append(row("Assessment", a.getTitle()))
                .append(row("Duration", duration))
                .append(row("Opens", start))
                .append(row("Deadline", due))
                .append(row("Attempts allowed", String.valueOf(a.getMaxAttempts())));

        String intro = reminder
                ? "This is a friendly reminder that the following assessment is still waiting for you."
                : "You have been invited to complete the following assessment.";
        StringBuilder body = new StringBuilder()
                .append(p("Hello " + esc(user.getFullName()) + ","))
                .append(p(intro))
                .append(table(rows.toString()));
        if (customMessage != null && !customMessage.isBlank()) {
            body.append(box("Message from the assessor", esc(customMessage).replace("\n", "<br>")));
        }
        if (a.getInstructions() != null && !a.getInstructions().isBlank()) {
            body.append(box("Instructions", esc(a.getInstructions()).replace("\n", "<br>")));
        }
        body.append(button(link, reminder ? "Continue to assessment" : "Open assessment"))
                .append(small("The timer starts only when you press <b>Start</b> on the assessment page. "
                        + "Times are shown in UTC; the assessment page shows them in your local time zone. "
                        + "This link is personal - do not share it."));

        String text = (reminder ? "Reminder: " : "") + "Hello " + user.getFullName() + ",\n\n" + intro + "\n\n"
                + "Assessment: " + a.getTitle() + "\nDuration: " + duration + "\nOpens: " + start
                + "\nDeadline: " + due + "\n\n"
                + (customMessage == null || customMessage.isBlank() ? "" : "Message: " + customMessage + "\n\n")
                + (a.getInstructions() == null ? "" : "Instructions:\n" + a.getInstructions() + "\n\n")
                + "Open the assessment: " + link + "\n\nThis link is personal - do not share it.\n" + org;

        return new EmailRequestedEvent(user.getEmail(), user.getId(),
                reminder ? EmailTemplate.REMINDER : EmailTemplate.INVITATION, subject,
                layout(org, reminder ? "Assessment reminder" : "You're invited", body.toString()), text,
                "ASSIGNMENT", a.getId(), link);
    }

    public EmailRequestedEvent passwordReset(User user, String token, int validMinutes) {
        String org = settings.orgName();
        String link = props.frontendBaseUrl() + "/reset-password?token=" + token;
        String body = p("Hello " + esc(user.getFullName()) + ",")
                + p("We received a request to reset your password. The link below is valid for "
                + validMinutes + " minutes and can be used once.")
                + button(link, "Reset password")
                + small("If you did not request this, you can ignore this e-mail - your password will not change.");
        String text = "Hello " + user.getFullName() + ",\n\nReset your password (valid " + validMinutes
                + " minutes): " + link + "\n\nIf you did not request this, ignore this e-mail.\n" + org;
        return new EmailRequestedEvent(user.getEmail(), user.getId(), EmailTemplate.PASSWORD_RESET,
                "Reset your " + org + " password", layout(org, "Password reset", body), text, "USER", user.getId(), link);
    }

    public EmailRequestedEvent accountSetup(User user, String token, int validHours) {
        String org = settings.orgName();
        String link = props.frontendBaseUrl() + "/reset-password?token=" + token + "&setup=1";
        String body = p("Hello " + esc(user.getFullName()) + ",")
                + p("An account has been created for you on " + esc(org) + ". Choose a password to activate it. "
                + "This link is valid for " + validHours + " hours.")
                + button(link, "Set up my account");
        String text = "Hello " + user.getFullName() + ",\n\nAn account was created for you on " + org
                + ". Set your password (valid " + validHours + " hours): " + link + "\n";
        return new EmailRequestedEvent(user.getEmail(), user.getId(), EmailTemplate.ACCOUNT_SETUP,
                "Activate your " + org + " account", layout(org, "Welcome aboard", body), text, "USER", user.getId(), link);
    }

    public EmailRequestedEvent resultReleased(User user, Assignment a, Long attemptId) {
        String org = settings.orgName();
        String link = props.frontendBaseUrl() + "/candidate/attempts/" + attemptId + "/result";
        String body = p("Hello " + esc(user.getFullName()) + ",")
                + p("Your result for <b>" + esc(a.getTitle()) + "</b> is now available.")
                + button(link, "View my result");
        String text = "Hello " + user.getFullName() + ",\n\nYour result for " + a.getTitle()
                + " is available: " + link + "\n";
        return new EmailRequestedEvent(user.getEmail(), user.getId(), EmailTemplate.RESULT_RELEASED,
                "Your result is available - " + a.getTitle(), layout(org, "Result released", body), text,
                "ATTEMPT", attemptId, link);
    }

    static String formatDuration(int minutes) {
        if (minutes < 60) {
            return minutes + " minutes";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        return h + (h == 1 ? " hour" : " hours") + (m == 0 ? "" : " " + m + " minutes");
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    private static String p(String html) {
        return "<p style=\"margin:0 0 14px;color:#cbd5e1;font-size:15px;line-height:1.6\">" + html + "</p>";
    }

    private static String small(String html) {
        return "<p style=\"margin:18px 0 0;color:#94a3b8;font-size:12px;line-height:1.6\">" + html + "</p>";
    }

    private static String row(String label, String value) {
        return "<tr><td style=\"padding:8px 12px;color:#94a3b8;font-size:13px;white-space:nowrap\">" + esc(label)
                + "</td><td style=\"padding:8px 12px;color:#f1f5f9;font-size:14px;font-weight:600\">" + esc(value) + "</td></tr>";
    }

    private static String table(String rows) {
        return "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"width:100%;margin:6px 0 18px;"
                + "background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.08);border-radius:12px\">"
                + rows + "</table>";
    }

    // Business Gateways International brand colours (from the official logo).
    private static final String BGI_BLUE = "#006DB7";
    private static final String BGI_ORANGE = "#F48120";
    private static final String BGI_GRADIENT = "linear-gradient(135deg,#004B82," + BGI_BLUE + " 55%,#1E95E3)";

    private static String box(String title, String html) {
        return "<div style=\"margin:0 0 18px;padding:14px 16px;background:rgba(244,129,32,0.10);"
                + "border:1px solid rgba(244,129,32,0.40);border-radius:12px\">"
                + "<div style=\"color:#FFB679;font-size:12px;text-transform:uppercase;letter-spacing:.08em;margin-bottom:6px\">"
                + esc(title) + "</div><div style=\"color:#e2e8f0;font-size:14px;line-height:1.6\">" + html + "</div></div>";
    }

    private static String button(String href, String label) {
        // Solid colour first: clients without gradient support still render the brand blue.
        return "<div style=\"margin:22px 0 6px\"><a href=\"" + esc(href) + "\" style=\"display:inline-block;padding:13px 26px;"
                + "border-radius:999px;background:" + BGI_BLUE + ";background:" + BGI_GRADIENT + ";color:#ffffff;"
                + "font-weight:700;font-size:15px;text-decoration:none\">" + esc(label) + "</a></div>"
                + "<p style=\"margin:8px 0 0;color:#6E8BA8;font-size:12px;word-break:break-all\">" + esc(href) + "</p>";
    }

    /** Text wordmark (e-mail clients block SVG logos): "business" orange, "gateways" white, INTERNATIONAL spaced. */
    private static String wordmark() {
        return "<div style=\"font-size:22px;font-weight:700;letter-spacing:-.01em;line-height:1\">"
                + "<span style=\"color:" + BGI_ORANGE + "\">business</span><span style=\"color:#ffffff\">gateways</span></div>"
                + "<div style=\"color:#CDE6FA;font-size:9px;letter-spacing:.46em;margin-top:5px\">INTERNATIONAL</div>";
    }

    private static String layout(String org, String heading, String content) {
        return "<!doctype html><html><body style=\"margin:0;padding:0;background:#03111F;font-family:Segoe UI,Roboto,Helvetica,Arial,sans-serif\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"background:#03111F;padding:32px 12px\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:560px;background:#0A1E33;"
                + "border:1px solid rgba(124,194,245,0.18);border-radius:20px;overflow:hidden\">"
                + "<tr><td style=\"height:4px;background:" + BGI_ORANGE + ";font-size:0;line-height:0\">&nbsp;</td></tr>"
                + "<tr><td style=\"padding:24px 28px;background:" + BGI_BLUE + ";background:" + BGI_GRADIENT + "\">"
                + wordmark()
                + "<div style=\"color:#E3F1FC;font-size:12px;letter-spacing:.14em;text-transform:uppercase;margin-top:18px\">AssessHub</div>"
                + "<div style=\"color:#ffffff;font-size:24px;font-weight:800;margin-top:4px\">" + esc(heading) + "</div></td></tr>"
                + "<tr><td style=\"padding:28px\">" + content + "</td></tr></table>"
                + "<p style=\"color:#5B7894;font-size:11px;margin-top:16px\">Sent by " + esc(org) + " · AssessHub assessment platform</p>"
                + "</td></tr></table></body></html>";
    }
}
