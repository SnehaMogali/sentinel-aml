package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Alert;
import com.sentinel.aml.entity.AlertStatus;
import com.sentinel.aml.entity.DetectionRuleType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findAllByOrderByRiskScoreDesc();

    Optional<Alert> findFirstByCustomer_CustomerIdAndTriggeredRuleTypeAndStatus(
            String customerId, DetectionRuleType triggeredRuleType, AlertStatus status);
}
