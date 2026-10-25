package com.assesshub.web;

import com.assesshub.common.ApiResponse;
import com.assesshub.dto.AttemptDtos.AttemptViewDto;
import com.assesshub.dto.CandidateDtos.AssessmentInstructionsDto;
import com.assesshub.dto.CandidateDtos.MyAssessmentDto;
import com.assesshub.security.CurrentUser;
import com.assesshub.service.AssignmentService;
import com.assesshub.service.AttemptService;
import com.assesshub.service.CandidateAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assessments")
@Tag(name = "My assessments", description = "Candidate / assignee view of assigned assessments")
public class CandidateAssessmentController {

    private final CandidateAssessmentService candidateService;
    private final AttemptService attemptService;
    private final AssignmentService assignmentService;

    public CandidateAssessmentController(CandidateAssessmentService candidateService, AttemptService attemptService,
                                         AssignmentService assignmentService) {
        this.candidateService = candidateService;
        this.attemptService = attemptService;
        this.assignmentService = assignmentService;
    }

    @GetMapping("/my")
    @Operation(summary = "Assessments assigned to the current user with server-computed availability")
    public ApiResponse<List<MyAssessmentDto>> my() {
        return ApiResponse.ok(candidateService.listMine(CurrentUser.get()));
    }

    @GetMapping("/{assignmentId}")
    @Operation(summary = "Instructions and availability before starting")
    public ApiResponse<AssessmentInstructionsDto> instructions(@PathVariable Long assignmentId) {
        return ApiResponse.ok(candidateService.instructions(assignmentId, CurrentUser.get()));
    }

    @PostMapping("/{assignmentId}/start")
    @Operation(summary = "Start (or resume) a timed attempt",
            description = "The server records the start time, computes the deadline and enforces schedule and attempt limits.")
    public ApiResponse<AttemptViewDto> start(@PathVariable Long assignmentId) {
        return ApiResponse.ok(attemptService.start(assignmentId, CurrentUser.get()));
    }

    @GetMapping("/{assignmentId}/attachments/{attachmentId}")
    @Operation(summary = "Download a reference attachment (assigned candidates and staff)")
    public ResponseEntity<Resource> attachment(@PathVariable Long assignmentId, @PathVariable Long attachmentId) {
        AssignmentService.AttachmentDownload d = assignmentService.downloadAttachment(assignmentId, attachmentId, CurrentUser.get());
        return WebUtils.download(d.resource(), d.fileName());
    }
}
