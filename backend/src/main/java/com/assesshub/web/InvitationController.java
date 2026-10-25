package com.assesshub.web;

import com.assesshub.common.ApiResponse;
import com.assesshub.dto.MiscDtos.AcceptInvitationRequest;
import com.assesshub.dto.MiscDtos.AcceptInvitationResponse;
import com.assesshub.dto.MiscDtos.InvitationInfoDto;
import com.assesshub.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Invitations (public)", description = "Secure assessment links received by e-mail")
public class InvitationController {

    private final InvitationService invitationService;
    private final WebUtils webUtils;

    public InvitationController(InvitationService invitationService, WebUtils webUtils) {
        this.invitationService = invitationService;
        this.webUtils = webUtils;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Describe the assessment behind an invitation link")
    public ApiResponse<InvitationInfoDto> info(@PathVariable String token) {
        return ApiResponse.ok(invitationService.getInfo(token));
    }

    @PostMapping("/{token}/accept")
    @Operation(summary = "Accept an invitation and sign the candidate in",
            description = "Candidates are signed in by the link (optionally setting a password). Staff accounts must sign in with their password.")
    public ResponseEntity<ApiResponse<AcceptInvitationResponse>> accept(@PathVariable String token,
                                                                        @Valid @RequestBody(required = false) AcceptInvitationRequest request,
                                                                        HttpServletRequest http) {
        InvitationService.AcceptResult result = invitationService.accept(token, request, WebUtils.ip(http), WebUtils.userAgent(http));
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (result.auth() != null) {
            builder.header(HttpHeaders.SET_COOKIE, webUtils.refreshCookie(result.auth()).toString());
        }
        return builder.body(ApiResponse.ok(result.response()));
    }
}
