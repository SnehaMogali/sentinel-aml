package com.sentinel.aml.rule;

import com.sentinel.aml.entity.DetectionRuleType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Outcome of evaluating one detection rule against one customer's transaction history.
 * Carries the explanation and evidence needed to populate an Alert directly.
 */
@Getter
@Builder
public class RuleEvaluationResult {

    private final boolean triggered;
    private final DetectionRuleType ruleType;
    private final int riskScore;
    private final String explanation;
    private final List<String> evidenceTransactionIds;

    public static RuleEvaluationResult notTriggered() {
        return RuleEvaluationResult.builder().triggered(false).build();
    }
}
