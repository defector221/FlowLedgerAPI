package com.flowledger.auth.controller;

import com.flowledger.auth.entity.Role;
import com.flowledger.auth.repository.RoleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {
    private final RoleRepository roles;

    public RoleController(RoleRepository roles) {
        this.roles = roles;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return roles.findAll().stream()
                .filter(role -> !"SUPER_ADMIN".equals(role.getCode()))
                .map(role -> new RoleResponse(role.getCode(), role.getName(), role.getDescription()))
                .toList();
    }

    public record RoleResponse(String code, String name, String description) {}
}
