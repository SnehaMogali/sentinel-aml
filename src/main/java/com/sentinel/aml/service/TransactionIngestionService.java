package com.sentinel.aml.service;

import com.sentinel.aml.dto.TransactionIngestRequest;
import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.repository.AccountRepository;
import com.sentinel.aml.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles both bulk and single-transaction (streaming-style) ingestion: validates referential
 * integrity against known accounts, persists the transaction(s), then re-runs the detection
 * engine so alerts reflect the new data before the caller's request completes.
 */
@Service
@RequiredArgsConstructor
public class TransactionIngestionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AlertService alertService;
    private final AtomicLong transactionSequence = new AtomicLong(System.currentTimeMillis());

    @Transactional
    public int ingestAndDetect(List<TransactionIngestRequest> incomingTransactions) {
        Set<String> affectedCustomerIds = new LinkedHashSet<>();

        for (TransactionIngestRequest incomingTransaction : incomingTransactions) {
            Account account = accountRepository.findById(incomingTransaction.getAccountId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Cannot ingest transaction: unknown accountId " + incomingTransaction.getAccountId()));

            Transaction transaction = Transaction.builder()
                    .transactionId("TXN_STREAM_" + transactionSequence.incrementAndGet())
                    .account(account)
                    .amount(incomingTransaction.getAmount())
                    .currencyCode(incomingTransaction.getCurrencyCode())
                    .counterpartyCountryCode(incomingTransaction.getCounterpartyCountryCode())
                    .channel(incomingTransaction.getChannel())
                    .transactionTimestamp(incomingTransaction.getTransactionTimestamp())
                    .build();

            transactionRepository.save(transaction);
            affectedCustomerIds.add(account.getCustomer().getCustomerId());
        }

        return alertService.runDetectionForCustomers(affectedCustomerIds);
    }
}
