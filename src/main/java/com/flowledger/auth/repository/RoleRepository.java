package com.flowledger.auth.repository;
import com.flowledger.auth.entity.Role; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface RoleRepository extends JpaRepository<Role,UUID> { Optional<Role> findByCode(String code); }
