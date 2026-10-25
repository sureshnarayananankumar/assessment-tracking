package com.assesshub.web;

import com.assesshub.common.ApiResponse;
import com.assesshub.common.PageResponse;
import com.assesshub.domain.enums.RoleName;
import com.assesshub.domain.enums.UserStatus;
import com.assesshub.dto.UserDtos.CreateUserRequest;
import com.assesshub.dto.UserDtos.UpdateUserRequest;
import com.assesshub.dto.UserDtos.UserDto;
import com.assesshub.security.CurrentUser;
import com.assesshub.service.UserService;
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

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@Tag(name = "Users", description = "User administration (Super Admin / Admin)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Search users (paginated)")
    public ApiResponse<PageResponse<UserDto>> list(@RequestParam(required = false) String search,
                                                   @RequestParam(required = false) RoleName role,
                                                   @RequestParam(required = false) UserStatus status,
                                                   @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(userService.search(search, role, status, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a user and (optionally) e-mail an account set-up link")
    public ApiResponse<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userService.create(request, CurrentUser.get()), "User created");
    }

    @GetMapping("/{id}")
    public ApiResponse<UserDto> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update profile, roles and status")
    public ApiResponse<UserDto> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.update(id, request, CurrentUser.get()), "User updated");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a user (soft delete; history is preserved)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deactivate(id, CurrentUser.get());
        return ApiResponse.message("User deactivated");
    }

    @PostMapping("/{id}/send-setup")
    @Operation(summary = "Re-send the account set-up / password e-mail")
    public ApiResponse<Void> sendSetup(@PathVariable Long id) {
        userService.resendSetup(id, CurrentUser.get());
        return ApiResponse.message("E-mail sent");
    }
}
