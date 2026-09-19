package com.sentinel.aml.rule;

import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.Transaction;

import java.util.List;

/**
 * A single AML typology check. Implementations evaluate one customer's full transaction
 * history and, when the typology's condition is met, return a triggered RuleEvaluationResult
 * carrying a risk score, a human-readable explanation, and the supporting transaction IDs.
 */
public interface DetectionRule {

    RuleEvaluationResult evaluate(Customer customer, List<Transaction> customerTransactions);
}
