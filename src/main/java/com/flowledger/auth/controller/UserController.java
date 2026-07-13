package com.flowledger.auth.controller;

import com.flowledger.auth.dto.UserDtos.*;
import com.flowledger.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public List<UserListResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public UserListResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public UserListResponse invite(@Valid @RequestBody InviteUserRequest request) {
        return service.invite(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public UserListResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public UserListResponse changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        return service.changeRole(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public UserListResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public UserListResponse deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }

    @PostMapping("/{id}/resend-invitation")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public UserListResponse resendInvitation(@PathVariable UUID id) {
        return service.resendInvitation(id);
    }
}
