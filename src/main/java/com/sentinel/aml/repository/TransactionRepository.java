package com.sentinel.aml.repository;

import com.sentinel.aml.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findByAccount_AccountIdOrderByTransactionTimestampAsc(String accountId);

    List<Transaction> findByAccount_Customer_CustomerIdOrderByTransactionTimestampAsc(String customerId);
}
