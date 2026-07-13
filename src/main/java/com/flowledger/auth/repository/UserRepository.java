package com.flowledger.auth.repository;

import com.flowledger.auth.entity.User;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdAndActiveTrue(UUID id);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    Optional<User> findByPasswordResetToken(String passwordResetToken);

    boolean existsByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    List<User> findByOrganizationId(UUID organizationId);

    Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<User> findByInvitationToken(String invitationToken);
}
