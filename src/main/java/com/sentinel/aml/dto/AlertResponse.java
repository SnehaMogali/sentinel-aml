package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Alert;
import com.sentinel.aml.entity.AlertStatus;
import com.sentinel.aml.entity.DetectionRuleType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class AlertResponse {

    private Long alertId;
    private String customerId;
    private DetectionRuleType triggeredRuleType;
    private Integer riskScore;
    private String explanation;
    private List<String> evidenceTransactionIds;
    private AlertStatus status;
    private Instant createdAt;

    public static AlertResponse fromEntity(Alert alert) {
        return AlertResponse.builder()
                .alertId(alert.getAlertId())
                .customerId(alert.getCustomer().getCustomerId())
                .triggeredRuleType(alert.getTriggeredRuleType())
                .riskScore(alert.getRiskScore())
                .explanation(alert.getExplanation())
                .evidenceTransactionIds(alert.getEvidenceTransactionIds())
                .status(alert.getStatus())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
