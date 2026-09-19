package com.sentinel.aml.service;

import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.rule.DetectionRule;
import com.sentinel.aml.rule.RuleEvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Runs every registered DetectionRule against one customer's transaction history and
 * returns only the rules that triggered. Spring injects every DetectionRule bean here,
 * so adding a new typology later is just adding a new @Component - no changes needed here.
 */
@Service
@RequiredArgsConstructor
public class DetectionEngine {

    private final List<DetectionRule> registeredDetectionRules;

    public List<RuleEvaluationResult> evaluateCustomer(Customer customer, List<Transaction> customerTransactions) {
        return registeredDetectionRules.stream()
                .map(rule -> rule.evaluate(customer, customerTransactions))
                .filter(RuleEvaluationResult::isTriggered)
                .toList();
    }
}
