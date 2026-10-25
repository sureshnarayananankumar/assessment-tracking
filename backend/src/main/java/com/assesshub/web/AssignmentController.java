package com.assesshub.web;

import com.assesshub.common.ApiResponse;
import com.assesshub.common.PageResponse;
import com.assesshub.domain.enums.AssigneeStatus;
import com.assesshub.domain.enums.AssignmentStatus;
import com.assesshub.domain.enums.AttemptStatus;
import com.assesshub.domain.enums.ResultGradingStatus;
import com.assesshub.dto.AssignmentDtos.AssigneeDto;
import com.assesshub.dto.AssignmentDtos.AssignmentDto;
import com.assesshub.dto.AssignmentDtos.AssignmentRequest;
import com.assesshub.dto.AssignmentDtos.AttachmentDto;
import com.assesshub.dto.AssignmentDtos.InviteRequest;
import com.assesshub.dto.AssignmentDtos.InviteResultDto;
import com.assesshub.dto.AssignmentDtos.StatusChangeRequest;
import com.assesshub.dto.MiscDtos.MonitoringAttemptDto;
import com.assesshub.dto.ResultDtos.ResultDto;
import com.assesshub.security.CurrentUser;
import com.assesshub.service.AssignmentService;
import com.assesshub.service.InvitationService;
import com.assesshub.service.ResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/assignments")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
@Tag(name = "Assignments", description = "Assessment definitions, publication, invitations and results (staff)")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final InvitationService invitationService;
    private final ResultService resultService;

    public AssignmentController(AssignmentService assignmentService, InvitationService invitationService,
                                ResultService resultService) {
        this.assignmentService = assignmentService;
        this.invitationService = invitationService;
        this.resultService = resultService;
    }

    @GetMapping
    @Operation(summary = "Search assessments (recruiters see their own; admins see all)")
    public ApiResponse<PageResponse<AssignmentDto>> list(@RequestParam(required = false) String search,
                                                         @RequestParam(required = false) AssignmentStatus status,
                                                         @RequestParam(required = false) String skill,
                                                         @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(assignmentService.search(CurrentUser.get(), search, status, skill, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssignmentDto> create(@Valid @RequestBody AssignmentRequest request) {
        return ApiResponse.ok(assignmentService.create(request, CurrentUser.get()), "Assessment created");
    }

    @GetMapping("/{id}")
    public ApiResponse<AssignmentDto> get(@PathVariable Long id) {
        return ApiResponse.ok(assignmentService.get(id, CurrentUser.get()));
    }

    @PutMapping("/{id}")
    public ApiResponse<AssignmentDto> update(@PathVariable Long id, @Valid @RequestBody AssignmentRequest request) {
        return ApiResponse.ok(assignmentService.update(id, request, CurrentUser.get()), "Assessment updated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an assessment without attempts (otherwise archive it)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        assignmentService.delete(id, CurrentUser.get());
        return ApiResponse.message("Assessment deleted");
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<AssignmentDto> publish(@PathVariable Long id) {
        return ApiResponse.ok(assignmentService.publish(id, CurrentUser.get()), "Assessment published");
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Close (COMPLETED), archive, reopen (ACTIVE) or unpublish (DRAFT)")
    public ApiResponse<AssignmentDto> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return ApiResponse.ok(assignmentService.changeStatus(id, request.status(), CurrentUser.get()), "Status updated");
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AssignmentDto> duplicate(@PathVariable Long id) {
        return ApiResponse.ok(assignmentService.duplicate(id, CurrentUser.get()), "Assessment duplicated");
    }

    // ---------------------------------------------------------------- invitations

    @PostMapping("/{id}/invite")
    @Operation(summary = "Assign the assessment to one or more people and e-mail secure links")
    public ApiResponse<InviteResultDto> invite(@PathVariable Long id, @Valid @RequestBody InviteRequest request) {
        return ApiResponse.ok(invitationService.invite(id, request, CurrentUser.get()), "Invitations processed");
    }

    @GetMapping("/{id}/assignees")
    public ApiResponse<PageResponse<AssigneeDto>> assignees(@PathVariable Long id,
                                                            @RequestParam(required = false) AssigneeStatus status,
                                                            @RequestParam(required = false) String search,
                                                            @PageableDefault(size = 20, sort = "assignedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(invitationService.listAssignees(id, status, search, pageable, CurrentUser.get()));
    }

    @PostMapping("/{id}/assignees/{assigneeId}/remind")
    @Operation(summary = "Send a reminder with a fresh link (previous link is revoked)")
    public ApiResponse<Void> remind(@PathVariable Long id, @PathVariable Long assigneeId) {
        invitationService.remind(id, assigneeId, CurrentUser.get());
        return ApiResponse.message("Reminder sent");
    }

    @PostMapping("/{id}/assignees/{assigneeId}/revoke")
    @Operation(summary = "Revoke access (invitation links stop working; a running attempt is ended)")
    public ApiResponse<Void> revoke(@PathVariable Long id, @PathVariable Long assigneeId) {
        invitationService.revoke(id, assigneeId, CurrentUser.get());
        return ApiResponse.message("Access revoked");
    }

    // ---------------------------------------------------------------- attempts & results

    @GetMapping("/{id}/attempts")
    public ApiResponse<PageResponse<MonitoringAttemptDto>> attempts(@PathVariable Long id,
                                                                    @RequestParam(required = false) AttemptStatus status,
                                                                    @RequestParam(required = false) Boolean flagged,
                                                                    @RequestParam(required = false) String search,
                                                                    @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        assignmentService.get(id, CurrentUser.get()); // ownership check
        return ApiResponse.ok(resultService.monitor(CurrentUser.get(), id, status, flagged, search, pageable));
    }

    @GetMapping("/{id}/results")
    public ApiResponse<PageResponse<ResultDto>> results(@PathVariable Long id,
                                                        @RequestParam(required = false) Boolean passed,
                                                        @RequestParam(required = false) ResultGradingStatus gradingStatus,
                                                        @RequestParam(required = false) Boolean flagged,
                                                        @RequestParam(required = false) String search,
                                                        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(resultService.search(CurrentUser.get(), id, passed, gradingStatus, flagged, search, pageable));
    }

    @GetMapping(value = "/{id}/results/export", produces = "text/csv")
    @Operation(summary = "Export results as CSV (opens in Excel)")
    public ResponseEntity<byte[]> exportResults(@PathVariable Long id) {
        String csv = resultService.exportCsv(CurrentUser.get(), id, null, null, null, null);
        return WebUtils.csv(csv, "assessment-" + id + "-results.csv");
    }

    @PostMapping("/{id}/release-results")
    @Operation(summary = "Release all graded results to candidates")
    public ApiResponse<Void> releaseResults(@PathVariable Long id) {
        return ApiResponse.message(assignmentService.releaseResults(id, CurrentUser.get()));
    }

    // ---------------------------------------------------------------- attachments

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a reference document for candidates")
    public ApiResponse<AttachmentDto> upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(assignmentService.uploadAttachment(id, file, CurrentUser.get()), "File uploaded");
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ApiResponse<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        assignmentService.deleteAttachment(id, attachmentId, CurrentUser.get());
        return ApiResponse.message("Attachment removed");
    }
}
