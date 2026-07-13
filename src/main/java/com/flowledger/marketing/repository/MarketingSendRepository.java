package com.flowledger.marketing.repository;

import com.flowledger.marketing.entity.MarketingSend;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketingSendRepository extends JpaRepository<MarketingSend, UUID> {}
