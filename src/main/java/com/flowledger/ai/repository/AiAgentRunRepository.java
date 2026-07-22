package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiAgentRun;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAgentRunRepository extends JpaRepository<AiAgentRun, UUID> {}
