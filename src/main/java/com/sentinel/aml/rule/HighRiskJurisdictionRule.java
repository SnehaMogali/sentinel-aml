package com.sentinel.aml.rule;

import com.sentinel.aml.config.HighRiskCountryProperties;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.DetectionRuleType;
import com.sentinel.aml.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Business rule: any transaction whose counterparty jurisdiction appears on the configurable
 * high-risk/sanctions list must always generate an alert, regardless of amount.
 */
@Component
@RequiredArgsConstructor
public class HighRiskJurisdictionRule implements DetectionRule {

    private final HighRiskCountryProperties highRiskCountryProperties;

    @Override
    public RuleEvaluationResult evaluate(Customer customer, List<Transaction> customerTransactions) {
        List<String> highRiskCountryCodes = highRiskCountryProperties.getHighRiskCountries();

        List<Transaction> sanctionedJurisdictionTransactions = customerTransactions.stream()
                .filter(transaction -> highRiskCountryCodes.contains(transaction.getCounterpartyCountryCode()))
                .toList();

        if (sanctionedJurisdictionTransactions.isEmpty()) {
            return RuleEvaluationResult.notTriggered();
        }

        String involvedCountries = sanctionedJurisdictionTransactions.stream()
                .map(Transaction::getCounterpartyCountryCode)
                .distinct()
                .collect(Collectors.joining(", "));

        String explanation = String.format(
                "%d transaction(s) involved counterparty jurisdiction(s) on the high-risk/sanctions list [%s] - "
                        + "flagged regardless of amount per configured jurisdiction policy.",
                sanctionedJurisdictionTransactions.size(), involvedCountries);

        return RuleEvaluationResult.builder()
                .triggered(true)
                .ruleType(DetectionRuleType.HIGH_RISK_JURISDICTION)
                .riskScore(95)
                .explanation(explanation)
                .evidenceTransactionIds(sanctionedJurisdictionTransactions.stream().map(Transaction::getTransactionId).toList())
                .build();
    }
}
