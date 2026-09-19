package com.sentinel.aml.dto;

import com.sentinel.aml.entity.Case;
import com.sentinel.aml.entity.CaseDispositionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class CaseResponse {

    private Long caseId;
    private Long alertId;
    private CaseDispositionStatus dispositionStatus;
    private String dispositionReason;
    private String analystId;
    private Instant dispositionedAt;

    public static CaseResponse fromEntity(Case caseRecord) {
        return CaseResponse.builder()
                .caseId(caseRecord.getCaseId())
                .alertId(caseRecord.getAlert().getAlertId())
                .dispositionStatus(caseRecord.getDispositionStatus())
                .dispositionReason(caseRecord.getDispositionReason())
                .analystId(caseRecord.getAnalystId())
                .dispositionedAt(caseRecord.getDispositionedAt())
                .build();
    }
}
