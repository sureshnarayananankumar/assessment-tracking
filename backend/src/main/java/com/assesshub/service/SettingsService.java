package com.assesshub.service;

import com.assesshub.common.exception.ApiException;
import com.assesshub.domain.AppSetting;
import com.assesshub.domain.enums.ReleasePolicy;
import com.assesshub.domain.enums.ViolationAction;
import com.assesshub.dto.MiscDtos.PublicSettingsDto;
import com.assesshub.dto.MiscDtos.SettingDto;
import com.assesshub.repository.AppSettingRepository;
import com.assesshub.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Application-wide settings stored in {@code app_settings}. Only known keys with valid values
 * can be written; missing rows fall back to safe defaults.
 */
@Service
public class SettingsService {

    public static final String ORG_NAME = "org.name";
    public static final String INVITATION_EXPIRY_HOURS = "invitation.expiry.hours";
    public static final String DEFAULT_GRACE_SECONDS = "assessment.default.grace.seconds";
    public static final String DEFAULT_DURATION_MINUTES = "assessment.default.duration.minutes";
    public static final String DEFAULT_WARNING_THRESHOLD = "monitoring.default.warning.threshold";
    public static final String DEFAULT_MAX_VIOLATIONS = "monitoring.default.max.violations";
    public static final String DEFAULT_VIOLATION_ACTION = "monitoring.default.action";
    public static final String DEFAULT_RELEASE_POLICY = "results.default.release.policy";
    public static final String REMINDERS_ENABLED = "email.reminders.enabled";
    public static final String REMINDER_HOURS_BEFORE = "email.reminder.hours.before";

    private record Definition(String defaultValue, String description, Predicate<String> validator) {
    }

    private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();

    static {
        DEFINITIONS.put(ORG_NAME, new Definition("Business Gateways International", "Organisation name shown in the UI and emails",
                v -> !v.isBlank() && v.length() <= 100));
        DEFINITIONS.put(INVITATION_EXPIRY_HOURS, new Definition("168", "Default lifetime of invitation links in hours",
                intBetween(1, 24 * 90)));
        DEFINITIONS.put(DEFAULT_GRACE_SECONDS, new Definition("30", "Default grace period accepted after the deadline",
                intBetween(0, 600)));
        DEFINITIONS.put(DEFAULT_DURATION_MINUTES, new Definition("60", "Default duration for new assessments",
                intBetween(1, 1440)));
        DEFINITIONS.put(DEFAULT_WARNING_THRESHOLD, new Definition("3", "Focus-loss events before a stronger warning",
                intBetween(1, 100)));
        DEFINITIONS.put(DEFAULT_MAX_VIOLATIONS, new Definition("5", "Focus-loss events before the configured action",
                intBetween(1, 100)));
        DEFINITIONS.put(DEFAULT_VIOLATION_ACTION, new Definition("FLAG_FOR_REVIEW", "WARN_ONLY, FLAG_FOR_REVIEW or AUTO_SUBMIT",
                enumValue(ViolationAction.class)));
        DEFINITIONS.put(DEFAULT_RELEASE_POLICY, new Definition("AFTER_GRADING", "AFTER_GRADING or MANUAL",
                enumValue(ReleasePolicy.class)));
        DEFINITIONS.put(REMINDERS_ENABLED, new Definition("true", "Send automatic reminders before deadlines",
                v -> v.equals("true") || v.equals("false")));
        DEFINITIONS.put(REMINDER_HOURS_BEFORE, new Definition("24", "Hours before the deadline to send the reminder",
                intBetween(1, 168)));
    }

    private final AppSettingRepository repository;
    private final AuditService auditService;

    public SettingsService(AppSettingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    private static Predicate<String> intBetween(int min, int max) {
        return v -> {
            try {
                int n = Integer.parseInt(v.trim());
                return n >= min && n <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        };
    }

    private static <E extends Enum<E>> Predicate<String> enumValue(Class<E> type) {
        return v -> Arrays.stream(type.getEnumConstants()).anyMatch(c -> c.name().equals(v));
    }

    @Transactional(readOnly = true)
    public String getString(String key) {
        Definition def = DEFINITIONS.get(key);
        String fallback = def == null ? null : def.defaultValue();
        return repository.findById(key).map(AppSetting::getValue).orElse(fallback);
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(getString(key).trim());
        } catch (RuntimeException e) {
            return Integer.parseInt(DEFINITIONS.get(key).defaultValue());
        }
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(getString(key));
    }

    public <E extends Enum<E>> E getEnum(String key, Class<E> type) {
        try {
            return Enum.valueOf(type, getString(key));
        } catch (RuntimeException e) {
            return Enum.valueOf(type, DEFINITIONS.get(key).defaultValue());
        }
    }

    public String orgName() {
        return getString(ORG_NAME);
    }

    @Transactional(readOnly = true)
    public List<SettingDto> getAll() {
        Map<String, AppSetting> stored = new LinkedHashMap<>();
        repository.findAll().forEach(s -> stored.put(s.getKey(), s));
        return DEFINITIONS.entrySet().stream()
                .map(e -> {
                    AppSetting s = stored.get(e.getKey());
                    return new SettingDto(e.getKey(), s == null ? e.getValue().defaultValue() : s.getValue(),
                            e.getValue().description(), s == null ? null : s.getUpdatedAt());
                })
                .sorted(Comparator.comparing(SettingDto::key))
                .toList();
    }

    public PublicSettingsDto getPublic() {
        return new PublicSettingsDto(orgName());
    }

    @Transactional
    public List<SettingDto> update(Map<String, String> values) {
        Map<String, String> errors = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            Definition def = DEFINITIONS.get(key);
            if (def == null) {
                errors.put(key, "Unknown setting");
            } else if (value == null || !def.validator().test(value.trim())) {
                errors.put(key, "Invalid value");
            }
        });
        if (!errors.isEmpty()) {
            throw ApiException.badRequest("INVALID_SETTINGS", "Invalid settings: " + errors);
        }
        Long actorId = CurrentUser.find().map(p -> p.getId()).orElse(null);
        values.forEach((key, value) -> {
            AppSetting setting = repository.findById(key)
                    .orElseGet(() -> new AppSetting(key, value.trim(), DEFINITIONS.get(key).description()));
            setting.setValue(value.trim());
            setting.setUpdatedBy(actorId);
            repository.save(setting);
        });
        auditService.log("SETTINGS_UPDATED", "SETTINGS", null, "Keys: " + String.join(", ", values.keySet()));
        return getAll();
    }
}
