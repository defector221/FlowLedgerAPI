package com.flowledger.commerce.order.repository;

import com.flowledger.commerce.order.entity.CommerceOrderReturnLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceOrderReturnLineRepository extends JpaRepository<CommerceOrderReturnLine, UUID> {}
