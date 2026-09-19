package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Case;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaseRepository extends JpaRepository<Case, Long> {

    Optional<Case> findByAlert_AlertId(Long alertId);
}
