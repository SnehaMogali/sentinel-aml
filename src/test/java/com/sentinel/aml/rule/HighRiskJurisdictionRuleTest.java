package com.sentinel.aml.rule;

import com.sentinel.aml.config.HighRiskCountryProperties;
import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HighRiskJurisdictionRuleTest {

    private HighRiskJurisdictionRule highRiskJurisdictionRule;
    private Customer testCustomer;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        HighRiskCountryProperties highRiskCountryProperties = new HighRiskCountryProperties();
        highRiskCountryProperties.setHighRiskCountries(List.of("IR", "KP", "SY"));
        highRiskJurisdictionRule = new HighRiskJurisdictionRule(highRiskCountryProperties);

        testCustomer = Customer.builder().customerId("CUST_00002").fullName("Test Customer").build();
        testAccount = Account.builder().accountId("ACC_000003").customer(testCustomer).currencyCode("INR").build();
    }

    @Test
    void triggersRegardlessOfAmountWhenCounterpartyCountryIsOnTheHighRiskList() {
        Transaction sanctionedJurisdictionTransaction = buildTransaction("TXN_001", new BigDecimal("50.00"), "KP");

        RuleEvaluationResult result = highRiskJurisdictionRule.evaluate(testCustomer, List.of(sanctionedJurisdictionTransaction));

        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getEvidenceTransactionIds()).containsExactly("TXN_001");
        assertThat(result.getRiskScore()).isEqualTo(95);
    }

    @Test
    void doesNotTriggerWhenCounterpartyCountryIsNotOnTheHighRiskList() {
        Transaction domesticTransaction = buildTransaction("TXN_002", new BigDecimal("50000.00"), "IN");

        RuleEvaluationResult result = highRiskJurisdictionRule.evaluate(testCustomer, List.of(domesticTransaction));

        assertThat(result.isTriggered()).isFalse();
    }

    @Test
    void aggregatesAllSanctionedJurisdictionTransactionsIntoOneResult() {
        List<Transaction> transactions = List.of(
                buildTransaction("TXN_001", new BigDecimal("100.00"), "KP"),
                buildTransaction("TXN_002", new BigDecimal("200.00"), "IN"),
                buildTransaction("TXN_003", new BigDecimal("300.00"), "IR"));

        RuleEvaluationResult result = highRiskJurisdictionRule.evaluate(testCustomer, transactions);

        assertThat(result.isTriggered()).isTrue();
        assertThat(result.getEvidenceTransactionIds()).containsExactlyInAnyOrder("TXN_001", "TXN_003");
    }

    private Transaction buildTransaction(String transactionId, BigDecimal amount, String counterpartyCountryCode) {
        return Transaction.builder()
                .transactionId(transactionId)
                .account(testAccount)
                .amount(amount)
                .currencyCode("INR")
                .counterpartyCountryCode(counterpartyCountryCode)
                .channel("WIRE_TRANSFER")
                .transactionTimestamp(Instant.parse("2026-09-14T11:00:00Z"))
                .build();
    }
}
