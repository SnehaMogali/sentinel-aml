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

class LargeTransactionRuleTest {

    private LargeTransactionRule largeTransactionRule;
    private Customer testCustomer;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        DetectionRuleThresholdProperties thresholdProperties = new DetectionRuleThresholdProperties();
        thresholdProperties.getLargeTransaction().setThreshold(new BigDecimal("10000"));
        largeTransactionRule = new LargeTransactionRule(thresholdProperties);

        testCustomer = Customer.builder().customerId("CUST_00001").fullName("Test Customer").build();
        testAccount = Account.builder().accountId("ACC_000001").customer(testCustomer).currencyCode("INR").build();
    }

    @Test
    void triggersWhenSingleTransactionMeetsOrExceedsThreshold() {
        Transaction largeTransaction = buildTransaction("TXN_LARGE", new BigDecimal("15000"), Instant.parse("2026-09-12T14:00:00Z"));

        RuleEvaluationResult result = largeTransactionRule.evaluate(testCustomer, List.of(largeTransaction));

        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getEvidenceTransactionIds()).containsExactly("TXN_LARGE");
        assertThat(result.getRiskScore()).isGreaterThanOrEqualTo(70);
    }

    @Test
    void doesNotTriggerWhenAllTransactionsAreBelowThreshold() {
        Transaction smallTransaction = buildTransaction("TXN_SMALL", new BigDecimal("2500"), Instant.parse("2026-09-12T14:00:00Z"));

        RuleEvaluationResult result = largeTransactionRule.evaluate(testCustomer, List.of(smallTransaction));

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
