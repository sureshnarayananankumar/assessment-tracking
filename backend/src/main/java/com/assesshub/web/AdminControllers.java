package com.assesshub.web;

/*
 * Small read-mostly controllers grouped in one file for navigation convenience.
 * Each is a separate Spring bean (package-private top-level classes).
 */

import com.assesshub.common.ApiResponse;
import com.assesshub.common.PageResponse;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.ResultGradingStatus;
import com.assesshub.dto.MiscDtos.AuditLogDto;
import com.assesshub.dto.MiscDtos.EmailNotificationDto;
import com.assesshub.dto.MiscDtos.MonitoringAttemptDto;
import com.assesshub.dto.MiscDtos.PublicSettingsDto;
import com.assesshub.dto.MiscDtos.SettingDto;
import com.assesshub.dto.MiscDtos.StaffDashboardDto;
import com.assesshub.dto.MiscDtos.UpdateSettingsRequest;
import com.assesshub.dto.ResultDtos.ResultDto;
import com.assesshub.repository.EmailNotificationRepository;
import com.assesshub.security.CurrentUser;
import com.assesshub.service.AuditService;
import com.assesshub.service.DashboardService;
import com.assesshub.service.ResultService;
import com.assesshub.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
class DashboardController {

    private final DashboardService dashboardService;

    DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
    @Operation(summary = "Statistics for administrators (all) and recruiters (own assessments)")
    public ApiResponse<StaffDashboardDto> staff() {
        return ApiResponse.ok(dashboardService.staffDashboard(CurrentUser.get()));
    }
}

@RestController
@RequestMapping("/api/monitoring")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
@Tag(name = "Monitoring", description = "Live and historical attempt monitoring")
class MonitoringController {

    private final ResultService resultService;

    MonitoringController(ResultService resultService) {
        this.resultService = resultService;
    }

    @GetMapping("/attempts")
    @Operation(summary = "Attempts with timing and focus-activity counters")
    public ApiResponse<PageResponse<MonitoringAttemptDto>> attempts(@RequestParam(required = false) Long assignmentId,
                                                                    @RequestParam(required = false) AttemptStatus status,
                                                                    @RequestParam(required = false) Boolean flagged,
                                                                    @RequestParam(required = false) String search,
                                                                    @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(resultService.monitor(CurrentUser.get(), assignmentId, status, flagged, search, pageable));
    }
}

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
@Tag(name = "Reports")
class ReportController {

    private final ResultService resultService;

    ReportController(ResultService resultService) {
        this.resultService = resultService;
    }

    @GetMapping("/results")
    public ApiResponse<PageResponse<ResultDto>> results(@RequestParam(required = false) Long assignmentId,
                                                        @RequestParam(required = false) Boolean passed,
                                                        @RequestParam(required = false) ResultGradingStatus gradingStatus,
                                                        @RequestParam(required = false) Boolean flagged,
                                                        @RequestParam(required = false) String search,
                                                        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(resultService.search(CurrentUser.get(), assignmentId, passed, gradingStatus, flagged, search, pageable));
    }

    @GetMapping(value = "/results/export", produces = "text/csv")
    @Operation(summary = "Export filtered results as CSV")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long assignmentId,
                                         @RequestParam(required = false) Boolean passed,
                                         @RequestParam(required = false) ResultGradingStatus gradingStatus,
                                         @RequestParam(required = false) Boolean flagged,
                                         @RequestParam(required = false) String search) {
        return WebUtils.csv(resultService.exportCsv(CurrentUser.get(), assignmentId, passed, gradingStatus, flagged, search),
                "assessment-results.csv");
    }
}

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@Tag(name = "Audit logs")
class AuditLogController {

    private final AuditService auditService;

    AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AuditLogDto>> search(@RequestParam(required = false) String action,
                                                         @RequestParam(required = false) String entityType,
                                                         @RequestParam(required = false) String search,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                         @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(auditService.search(action, entityType, search, from, to, pageable));
    }
}

@RestController
@RequestMapping("/api/email-notifications")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@Tag(name = "E-mail log")
class EmailNotificationController {

    private final EmailNotificationRepository repository;

    EmailNotificationController(EmailNotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Recent e-mail deliveries (no message bodies or links are stored)")
    public ApiResponse<PageResponse<EmailNotificationDto>> list(@PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(repository.findAllByOrderByCreatedAtDesc(pageable),
                n -> new EmailNotificationDto(n.getId(), n.getRecipientEmail(), n.getTemplate().name(), n.getSubject(),
                        n.getStatus().name(), n.getErrorMessage(), n.getRelatedType(), n.getRelatedId(), n.getSentAt(),
                        n.getCreatedAt())));
    }
}

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Settings")
class SettingsController {

    private final SettingsService settingsService;

    SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/public")
    @Operation(summary = "Public branding settings (no authentication)")
    public ApiResponse<PublicSettingsDto> publicSettings() {
        return ApiResponse.ok(settingsService.getPublic());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
    public ApiResponse<List<SettingDto>> all() {
        return ApiResponse.ok(settingsService.getAll());
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update application-wide settings (Super Admin)")
    public ApiResponse<List<SettingDto>> update(@Valid @RequestBody UpdateSettingsRequest request) {
        return ApiResponse.ok(settingsService.update(request.values()), "Settings saved");
    }
}
