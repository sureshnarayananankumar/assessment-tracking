package com.assesshub.web;

import com.assesshub.common.ApiResponse;
import com.assesshub.dto.AttemptDtos.ActivityBatchRequest;
import com.assesshub.dto.AttemptDtos.ActivityLogDto;
import com.assesshub.dto.AttemptDtos.ActivityResponse;
import com.assesshub.dto.AttemptDtos.AttemptViewDto;
import com.assesshub.dto.AttemptDtos.SaveAnswersRequest;
import com.assesshub.dto.AttemptDtos.SaveAnswersResponse;
import com.assesshub.dto.AttemptDtos.SavedAnswerDto;
import com.assesshub.dto.AttemptDtos.SubmitResponse;
import com.assesshub.dto.ResultDtos.AttemptReviewDto;
import com.assesshub.dto.ResultDtos.CandidateResultDto;
import com.assesshub.dto.ResultDtos.FlagRequest;
import com.assesshub.dto.ResultDtos.GradeRequest;
import com.assesshub.dto.ResultDtos.ResultDto;
import com.assesshub.security.CurrentUser;
import com.assesshub.service.AttemptService;
import com.assesshub.service.ResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/attempts")
@Tag(name = "Attempts", description = "Timed attempt engine, monitoring events, submission, review and grading")
public class AttemptController {

    private final AttemptService attemptService;
    private final ResultService resultService;

    public AttemptController(AttemptService attemptService, ResultService resultService) {
        this.attemptService = attemptService;
        this.resultService = resultService;
    }

    // ---------------------------------------------------------------- candidate

    @GetMapping("/{attemptId}")
    @Operation(summary = "Load an attempt with questions and saved answers (owner only); restores progress after refresh")
    public ApiResponse<AttemptViewDto> get(@PathVariable Long attemptId) {
        return ApiResponse.ok(attemptService.getView(attemptId, CurrentUser.get()));
    }

    @PutMapping("/{attemptId}/answers")
    @Operation(summary = "Autosave answers (rejected after the server deadline + grace period)")
    public ApiResponse<SaveAnswersResponse> saveAnswers(@PathVariable Long attemptId,
                                                        @Valid @RequestBody SaveAnswersRequest request) {
        return ApiResponse.ok(attemptService.saveAnswers(attemptId, request, CurrentUser.get()));
    }

    @PostMapping(value = "/{attemptId}/answers/{questionId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file answer for a FILE_UPLOAD question")
    public ApiResponse<SavedAnswerDto> uploadFile(@PathVariable Long attemptId, @PathVariable Long questionId,
                                                  @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(attemptService.uploadFile(attemptId, questionId, file, CurrentUser.get()), "File saved");
    }

    @GetMapping("/{attemptId}/answers/{questionId}/file")
    @Operation(summary = "Download an uploaded answer file (owner or authorised staff)")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long attemptId, @PathVariable Long questionId) {
        AttemptService.AnswerFile f = attemptService.downloadAnswerFile(attemptId, questionId, CurrentUser.get());
        return WebUtils.download(f.resource(), f.fileName());
    }

    @PostMapping("/{attemptId}/activity")
    @Operation(summary = "Record tab-visibility / window-focus events",
            description = "Counts are informational; repeated events may flag the attempt for human review per the assessment policy.")
    public ApiResponse<ActivityResponse> activity(@PathVariable Long attemptId, @Valid @RequestBody ActivityBatchRequest request) {
        return ApiResponse.ok(attemptService.recordActivity(attemptId, request, CurrentUser.get()));
    }

    @PostMapping("/{attemptId}/submit")
    @Operation(summary = "Submit the attempt (rejected after the server deadline + grace period)")
    public ApiResponse<SubmitResponse> submit(@PathVariable Long attemptId) {
        return ApiResponse.ok(attemptService.submit(attemptId, CurrentUser.get()));
    }

    @GetMapping("/{attemptId}/results")
    @Operation(summary = "Candidate view of their result (scores only after release)")
    public ApiResponse<CandidateResultDto> results(@PathVariable Long attemptId) {
        return ApiResponse.ok(resultService.candidateResult(attemptId, CurrentUser.get()));
    }

    // ---------------------------------------------------------------- staff

    @GetMapping("/{attemptId}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
    @Operation(summary = "Full attempt details with answer key for evaluators")
    public ApiResponse<AttemptReviewDto> review(@PathVariable Long attemptId) {
        return ApiResponse.ok(resultService.review(attemptId, CurrentUser.get()));
    }

    @GetMapping("/{attemptId}/activity")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
    @Operation(summary = "Activity / focus event history")
    public ApiResponse<List<ActivityLogDto>> activityLog(@PathVariable Long attemptId) {
        return ApiResponse.ok(resultService.activity(attemptId, CurrentUser.get()));
    }

    @PostMapping("/{attemptId}/grade")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
    @Operation(summary = "Grade answers manually (marks validated against the maximum on the server)")
    public ApiResponse<AttemptReviewDto> grade(@PathVariable Long attemptId, @Valid @RequestBody GradeRequest request) {
        return ApiResponse.ok(resultService.grade(attemptId, request, CurrentUser.get()), "Grades saved");
    }

    @PostMapping("/{attemptId}/flag")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
    public ApiResponse<AttemptReviewDto> flag(@PathVariable Long attemptId, @Valid @RequestBody FlagRequest request) {
        return ApiResponse.ok(resultService.flag(attemptId, request, CurrentUser.get()));
    }

    @PostMapping("/{attemptId}/release")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
    @Operation(summary = "Release a single graded result to the candidate")
    public ApiResponse<ResultDto> release(@PathVariable Long attemptId) {
        return ApiResponse.ok(resultService.releaseOne(attemptId, CurrentUser.get()), "Result released");
    }
}
