package com.sentinel.aml.rule;

import com.sentinel.aml.config.DetectionRuleThresholdProperties;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.DetectionRuleType;
import com.sentinel.aml.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business rule: any single transaction at or above the CTR-style threshold (default $10,000)
 * must be flagged for review, regardless of any other pattern.
 */
@Component
@RequiredArgsConstructor
public class LargeTransactionRule implements DetectionRule {

    private final DetectionRuleThresholdProperties thresholdProperties;

    @Override
    public RuleEvaluationResult evaluate(Customer customer, List<Transaction> customerTransactions) {
        BigDecimal reportingThreshold = thresholdProperties.getLargeTransaction().getThreshold();

        List<Transaction> qualifyingTransactions = customerTransactions.stream()
                .filter(transaction -> transaction.getAmount().compareTo(reportingThreshold) >= 0)
                .toList();

        if (qualifyingTransactions.isEmpty()) {
            return RuleEvaluationResult.notTriggered();
        }

        BigDecimal largestAmount = qualifyingTransactions.stream()
                .map(Transaction::getAmount)
                .max(BigDecimal::compareTo)
                .orElse(reportingThreshold);

        int riskScore = Math.min(100, 70 + qualifyingTransactions.size() * 5);

        String explanation = String.format(
                "%d transaction(s) met or exceeded the $%s CTR-style reporting threshold "
                        + "(largest: $%s). Each such transaction is auto-flagged regardless of any other pattern.",
                qualifyingTransactions.size(), reportingThreshold.toPlainString(), largestAmount.toPlainString());

        return RuleEvaluationResult.builder()
                .triggered(true)
                .ruleType(DetectionRuleType.LARGE_TRANSACTION)
                .riskScore(riskScore)
                .explanation(explanation)
                .evidenceTransactionIds(qualifyingTransactions.stream().map(Transaction::getTransactionId).toList())
                .build();
    }
}
