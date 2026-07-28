package com.flowledger.commerce.auth.repository;

import com.flowledger.commerce.auth.entity.CommerceOtpChallenge;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceOtpChallengeRepository extends JpaRepository<CommerceOtpChallenge, UUID> {
    Optional<CommerceOtpChallenge> findFirstByMobileAndConsumedFalseOrderByCreatedAtDesc(String mobile);
}
