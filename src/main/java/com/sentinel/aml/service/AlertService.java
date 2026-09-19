package com.sentinel.aml.service;

import com.sentinel.aml.entity.Alert;
import com.sentinel.aml.entity.AlertStatus;
import com.sentinel.aml.entity.Case;
import com.sentinel.aml.entity.CaseDispositionStatus;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.repository.AlertRepository;
import com.sentinel.aml.repository.CaseRepository;
import com.sentinel.aml.repository.CustomerRepository;
import com.sentinel.aml.repository.TransactionRepository;
import com.sentinel.aml.rule.RuleEvaluationResult;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates detection runs and owns alert lifecycle: creation, de-duplication against
 * still-open alerts for the same customer+rule, and disposition (which always creates/updates
 * a Case rather than deleting the Alert - alerts are never silently removed).
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;
    private final AlertRepository alertRepository;
    private final CaseRepository caseRepository;
    private final DetectionEngine detectionEngine;

    @Transactional
    public int runDetectionForAllCustomers() {
        List<String> allCustomerIds = customerRepository.findAll().stream().map(Customer::getCustomerId).toList();
        return runDetectionForCustomers(allCustomerIds);
    }

    /**
     * Scoped re-detection for just the customers touched by a new batch of transactions.
     * Used by streaming/incremental ingestion so that unrelated customers' already-dispositioned
     * alerts aren't needlessly re-evaluated (and potentially re-triggered) on every ingest call.
     */
    @Transactional
    public int runDetectionForCustomers(Iterable<String> customerIds) {
        int alertsCreatedOrUpdated = 0;

        for (String customerId : customerIds) {
            Customer customer = customerRepository.findById(customerId).orElse(null);
            if (customer == null) {
                continue;
            }

            List<Transaction> customerTransactions =
                    transactionRepository.findByAccount_Customer_CustomerIdOrderByTransactionTimestampAsc(customerId);

            if (customerTransactions.isEmpty()) {
                continue;
            }

            List<RuleEvaluationResult> triggeredRules = detectionEngine.evaluateCustomer(customer, customerTransactions);
            for (RuleEvaluationResult triggeredRule : triggeredRules) {
                applyRuleResult(customer, triggeredRule);
                alertsCreatedOrUpdated++;
            }
        }

        return alertsCreatedOrUpdated;
    }

    /**
     * De-duplication: if this customer already has an OPEN alert for this exact rule type,
     * merge the new evidence into it instead of spawning a redundant alert.
     */
    private void applyRuleResult(Customer customer, RuleEvaluationResult ruleResult) {
        alertRepository.findFirstByCustomer_CustomerIdAndTriggeredRuleTypeAndStatus(
                        customer.getCustomerId(), ruleResult.getRuleType(), AlertStatus.OPEN)
                .ifPresentOrElse(
                        existingAlert -> mergeEvidenceIntoExistingAlert(existingAlert, ruleResult),
                        () -> createNewAlert(customer, ruleResult));
    }

    private void mergeEvidenceIntoExistingAlert(Alert existingAlert, RuleEvaluationResult ruleResult) {
        Set<String> mergedEvidenceTransactionIds = new LinkedHashSet<>(existingAlert.getEvidenceTransactionIds());
        mergedEvidenceTransactionIds.addAll(ruleResult.getEvidenceTransactionIds());

        existingAlert.setEvidenceTransactionIds(new ArrayList<>(mergedEvidenceTransactionIds));
        existingAlert.setRiskScore(Math.max(existingAlert.getRiskScore(), ruleResult.getRiskScore()));
        existingAlert.setExplanation(ruleResult.getExplanation());
        alertRepository.save(existingAlert);
    }

    private void createNewAlert(Customer customer, RuleEvaluationResult ruleResult) {
        Alert newAlert = Alert.builder()
                .customer(customer)
                .triggeredRuleType(ruleResult.getRuleType())
                .riskScore(ruleResult.getRiskScore())
                .explanation(ruleResult.getExplanation())
                .evidenceTransactionIds(new ArrayList<>(ruleResult.getEvidenceTransactionIds()))
                .status(AlertStatus.OPEN)
                .createdAt(Instant.now())
                .build();
        alertRepository.save(newAlert);
    }

    public List<Alert> listAlertsSortedByRiskScoreDescending() {
        return alertRepository.findAllByOrderByRiskScoreDesc();
    }

    public Alert getAlertById(Long alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new EntityNotFoundException("Alert not found: " + alertId));
    }

    @Transactional
    public Case dispositionAlert(Long alertId, CaseDispositionStatus dispositionStatus, String dispositionReason, String analystId) {
        Alert alert = getAlertById(alertId);
        alert.setStatus(AlertStatus.DISPOSITIONED);
        alertRepository.save(alert);

        Case caseRecord = caseRepository.findByAlert_AlertId(alertId).orElseGet(() -> Case.builder().alert(alert).build());
        caseRecord.setDispositionStatus(dispositionStatus);
        caseRecord.setDispositionReason(dispositionReason);
        caseRecord.setAnalystId(analystId);
        caseRecord.setDispositionedAt(Instant.now());

        return caseRepository.save(caseRecord);
    }
}
