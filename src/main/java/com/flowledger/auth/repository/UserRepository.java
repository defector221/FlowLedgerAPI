package com.flowledger.auth.repository;
import com.flowledger.auth.entity.User; import org.springframework.data.jpa.repository.*; import java.util.*;
public interface UserRepository extends JpaRepository<User,UUID> { Optional<User> findByIdAndActiveTrue(UUID id); Optional<User> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId,String email); Optional<User> findByPasswordResetToken(String passwordResetToken); boolean existsByOrganizationIdAndEmailIgnoreCase(UUID organizationId,String email); }
