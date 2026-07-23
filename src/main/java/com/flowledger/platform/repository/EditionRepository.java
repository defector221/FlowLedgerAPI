package com.flowledger.platform.repository;

import com.flowledger.platform.entity.Edition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EditionRepository extends JpaRepository<Edition, String> {
    List<Edition> findAllByOrderByRankAsc();
}
