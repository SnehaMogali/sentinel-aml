package com.sentinel.aml.rule;

import com.sentinel.aml.config.DetectionRuleThresholdProperties;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.DetectionRuleType;
import com.sentinel.aml.entity.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business rule: three or more transactions from the same account, each individually falling
 * just under the CTR threshold ($9,000-$9,999 by default), within a rolling 24-hour window,
 * indicate deliberate structuring/smurfing to avoid the reporting threshold.
 */
@Component
@RequiredArgsConstructor
public class StructuringRule implements DetectionRule {

    private final DetectionRuleThresholdProperties thresholdProperties;

    @Override
    public RuleEvaluationResult evaluate(Customer customer, List<Transaction> customerTransactions) {
        DetectionRuleThresholdProperties.Structuring structuringConfig = thresholdProperties.getStructuring();
        BigDecimal minAmount = structuringConfig.getMinAmount();
        BigDecimal maxAmount = structuringConfig.getMaxAmount();
        Duration rollingWindow = Duration.ofHours(structuringConfig.getWindowHours());

        Map<String, List<Transaction>> transactionsByAccount = customerTransactions.stream()
                .filter(transaction -> isWithinStructuringBand(transaction.getAmount(), minAmount, maxAmount))
                .sorted((left, right) -> left.getTransactionTimestamp().compareTo(right.getTransactionTimestamp()))
                .collect(Collectors.groupingBy(transaction -> transaction.getAccount().getAccountId()));

        for (Map.Entry<String, List<Transaction>> accountEntry : transactionsByAccount.entrySet()) {
            List<Transaction> candidateTransactions = accountEntry.getValue();
            List<Transaction> matchingWindow = findQualifyingWindow(candidateTransactions, rollingWindow, structuringConfig.getMinCount());

            if (matchingWindow != null) {
                String explanation = String.format(
                        "Account %s made %d transactions of $%s-$%s within a %dh window "
                                + "(just under the $10,000 reporting threshold) - a classic structuring/smurfing pattern.",
                        accountEntry.getKey(), matchingWindow.size(), minAmount.toPlainString(),
                        maxAmount.toPlainString(), structuringConfig.getWindowHours());

                return RuleEvaluationResult.builder()
                        .triggered(true)
                        .ruleType(DetectionRuleType.STRUCTURING)
                        .riskScore(85)
                        .explanation(explanation)
                        .evidenceTransactionIds(matchingWindow.stream().map(Transaction::getTransactionId).toList())
                        .build();
            }
        }

        return RuleEvaluationResult.notTriggered();
    }

    private boolean isWithinStructuringBand(BigDecimal amount, BigDecimal minAmount, BigDecimal maxAmount) {
        return amount.compareTo(minAmount) >= 0 && amount.compareTo(maxAmount) <= 0;
    }

    /**
     * Slides a window over chronologically-sorted transactions and returns the first set of
     * at least minCount transactions that all fall within rollingWindow of each other.
     */
    private List<Transaction> findQualifyingWindow(List<Transaction> sortedTransactions, Duration rollingWindow, int minCount) {
        for (int windowStart = 0; windowStart < sortedTransactions.size(); windowStart++) {
            Instant windowStartTime = sortedTransactions.get(windowStart).getTransactionTimestamp();
            List<Transaction> transactionsInWindow = new ArrayList<>();

            for (int cursor = windowStart; cursor < sortedTransactions.size(); cursor++) {
                Transaction candidate = sortedTransactions.get(cursor);
                Duration elapsedSinceWindowStart = Duration.between(windowStartTime, candidate.getTransactionTimestamp());
                boolean withinWindow = elapsedSinceWindowStart.compareTo(rollingWindow) <= 0;
                if (!withinWindow) {
                    break;
                }
                transactionsInWindow.add(candidate);
            }

            if (transactionsInWindow.size() >= minCount) {
                return transactionsInWindow;
            }
        }
        return null;
    }
}
