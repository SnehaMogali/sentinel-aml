package com.sentinel.aml.controller;

import com.sentinel.aml.dto.AlertResponse;
import com.sentinel.aml.dto.CaseDispositionRequest;
import com.sentinel.aml.dto.CaseResponse;
import com.sentinel.aml.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Analyst-facing alert queue: list open/dispositioned alerts sorted by risk, inspect one in
 * detail (rule, explanation, evidence), and record a disposition without ever deleting the alert.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public List<AlertResponse> listAlertsSortedByRisk() {
        return alertService.listAlertsSortedByRiskScoreDescending().stream()
                .map(AlertResponse::fromEntity)
                .toList();
    }

    @GetMapping("/{alertId}")
    public AlertResponse getAlert(@PathVariable Long alertId) {
        return AlertResponse.fromEntity(alertService.getAlertById(alertId));
    }

    @PostMapping("/{alertId}/disposition")
    public ResponseEntity<CaseResponse> dispositionAlert(@PathVariable Long alertId,
                                                           @Valid @RequestBody CaseDispositionRequest dispositionRequest) {
        var caseRecord = alertService.dispositionAlert(
                alertId,
                dispositionRequest.getDispositionStatus(),
                dispositionRequest.getDispositionReason(),
                dispositionRequest.getAnalystId());
        return ResponseEntity.status(HttpStatus.OK).body(CaseResponse.fromEntity(caseRecord));
    }
}
