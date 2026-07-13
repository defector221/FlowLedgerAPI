package com.flowledger.auth.entity;

import com.flowledger.common.entity.AuditableEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditableEntity {
    private UUID organizationId;
    private UUID lastActiveOrganizationId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String firstName;

    private String lastName;
    private String phone;
    private boolean active = true;
    private boolean emailVerified;
    private String passwordResetToken;
    private Instant passwordResetExpiry;
    private Instant lastLoginAt;

    @Column(name = "user_status", nullable = false)
    private String userStatus = "ACTIVE";

    private String invitationToken;
    private Instant invitationExpiry;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();
}
