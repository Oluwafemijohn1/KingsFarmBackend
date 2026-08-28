package com.kingsfarm.kingsfarmbackend.user;

import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import com.kingsfarm.kingsfarmbackend.user.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Every endpoint here is Administrator-only — matches BACKEND_PLAN.md §4 ("only admin can create users"). */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUserResponse createUser(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                          @Valid @RequestBody CreateUserRequest request) {
        return service.createUser(request, principal.username());
    }

    @GetMapping
    public PageResponse<UserSummaryResponse> listUsers(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.listUsers(pageable));
    }

    @PutMapping("/{id}")
    public UserSummaryResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return service.updateUser(id, request);
    }

    @PatchMapping("/{id}/active")
    public UserSummaryResponse setActive(@PathVariable Long id, @Valid @RequestBody SetActiveRequest request) {
        return service.setActive(id, request.active());
    }

    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable Long id) {
        return service.resetPassword(id);
    }
}
