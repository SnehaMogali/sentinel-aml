package com.sentinel.aml.rule;

import com.sentinel.aml.config.DetectionRuleThresholdProperties;
import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StructuringRuleTest {

    private StructuringRule structuringRule;
    private Customer testCustomer;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        DetectionRuleThresholdProperties thresholdProperties = new DetectionRuleThresholdProperties();
        thresholdProperties.getStructuring().setMinAmount(new BigDecimal("9000"));
        thresholdProperties.getStructuring().setMaxAmount(new BigDecimal("9999"));
        thresholdProperties.getStructuring().setMinCount(3);
        thresholdProperties.getStructuring().setWindowHours(24);
        structuringRule = new StructuringRule(thresholdProperties);

        testCustomer = Customer.builder().customerId("CUST_00001").fullName("Test Customer").build();
        testAccount = Account.builder().accountId("ACC_000002").customer(testCustomer).currencyCode("INR").build();
    }

    @Test
    void triggersOnThreeJustBelowThresholdTransactionsWithinOneDay() {
        List<Transaction> transactions = List.of(
                buildTransaction("TXN_001", new BigDecimal("9200"), Instant.parse("2026-09-14T09:00:00Z")),
                buildTransaction("TXN_002", new BigDecimal("9500"), Instant.parse("2026-09-14T14:00:00Z")),
                buildTransaction("TXN_003", new BigDecimal("9800"), Instant.parse("2026-09-15T06:00:00Z")));

        RuleEvaluationResult result = structuringRule.evaluate(testCustomer, transactions);

        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getEvidenceTransactionIds()).containsExactlyInAnyOrder("TXN_001", "TXN_002", "TXN_003");
    }

    @Test
    void doesNotTriggerWhenTransactionsFallBelowTheStructuringBand() {
        List<Transaction> transactions = List.of(
                buildTransaction("TXN_001", new BigDecimal("8999"), Instant.parse("2026-09-14T09:00:00Z")),
                buildTransaction("TXN_002", new BigDecimal("8999"), Instant.parse("2026-09-14T14:00:00Z")),
                buildTransaction("TXN_003", new BigDecimal("8999"), Instant.parse("2026-09-15T06:00:00Z")));

        RuleEvaluationResult result = structuringRule.evaluate(testCustomer, transactions);

        assertThat(result.isTriggered()).isFalse();
    }

    @Test
    void doesNotTriggerWhenQualifyingTransactionsFallOutsideTheRollingWindow() {
        List<Transaction> transactions = List.of(
                buildTransaction("TXN_001", new BigDecimal("9200"), Instant.parse("2026-09-14T09:00:00Z")),
                buildTransaction("TXN_002", new BigDecimal("9500"), Instant.parse("2026-09-16T14:00:00Z")),
                buildTransaction("TXN_003", new BigDecimal("9800"), Instant.parse("2026-09-19T06:00:00Z")));

        RuleEvaluationResult result = structuringRule.evaluate(testCustomer, transactions);

        assertThat(result.isTriggered()).isFalse();
    }

    private Transaction buildTransaction(String transactionId, BigDecimal amount, Instant timestamp) {
        return Transaction.builder()
                .transactionId(transactionId)
                .account(testAccount)
                .amount(amount)
                .currencyCode("INR")
                .counterpartyCountryCode("IN")
                .channel("ONLINE")
                .transactionTimestamp(timestamp)
                .build();
    }
}
