package com.assesshub.web;

import com.assesshub.common.ApiResponse;
import com.assesshub.common.PageResponse;
import com.assesshub.domain.enums.Difficulty;
import com.assesshub.domain.enums.QuestionType;
import com.assesshub.dto.QuestionDtos.QuestionDto;
import com.assesshub.dto.QuestionDtos.QuestionRequest;
import com.assesshub.security.CurrentUser;
import com.assesshub.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/questions")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','RECRUITER')")
@Tag(name = "Question bank")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping
    @Operation(summary = "Search the question bank (own + shared for recruiters, all for admins)")
    public ApiResponse<PageResponse<QuestionDto>> list(@RequestParam(required = false) String search,
                                                       @RequestParam(required = false) QuestionType type,
                                                       @RequestParam(required = false) Difficulty difficulty,
                                                       @RequestParam(required = false) String skill,
                                                       @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(questionService.search(CurrentUser.get(), search, type, difficulty, skill, pageable));
    }

    @GetMapping("/skills")
    @Operation(summary = "Distinct skill categories in use")
    public ApiResponse<List<String>> skills() {
        return ApiResponse.ok(questionService.skillCategories());
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionDto> get(@PathVariable Long id) {
        return ApiResponse.ok(questionService.get(id, CurrentUser.get()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionDto> create(@Valid @RequestBody QuestionRequest request) {
        return ApiResponse.ok(questionService.create(request, CurrentUser.get()), "Question created");
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionDto> update(@PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        return ApiResponse.ok(questionService.update(id, request, CurrentUser.get()), "Question updated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete (or archive if already used) a question")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return ApiResponse.message(questionService.delete(id, CurrentUser.get()));
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<QuestionDto> duplicate(@PathVariable Long id) {
        return ApiResponse.ok(questionService.duplicate(id, CurrentUser.get()), "Question duplicated");
    }
}
