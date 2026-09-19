package com.sentinel.aml.service;

import com.sentinel.aml.entity.Account;
import com.sentinel.aml.entity.Customer;
import com.sentinel.aml.entity.Transaction;
import com.sentinel.aml.repository.AccountRepository;
import com.sentinel.aml.repository.CustomerRepository;
import com.sentinel.aml.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Loads the synthetic customers/accounts/transactions CSVs from the classpath on startup,
 * skipping malformed rows with a logged error rather than failing the whole load, then
 * triggers one detection run so alerts already exist by the time the API is queried.
 *
 * This stands in for the "bulk ingestion" requirement; POST /api/v1/ingest/transactions
 * (see IngestController) covers incremental/streaming ingestion of new transactions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedDataLoader implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AlertService alertService;

    @Override
    public void run(String... args) throws Exception {
        if (customerRepository.count() > 0) {
            log.info("Seed data already loaded, skipping CSV import.");
        } else {
            int customersLoaded = loadCustomers();
            int accountsLoaded = loadAccounts();
            int transactionsLoaded = loadTransactions();
            log.info("Seed data loaded: {} customers, {} accounts, {} transactions.",
                    customersLoaded, accountsLoaded, transactionsLoaded);
        }

        int alertsCreatedOrUpdated = alertService.runDetectionForAllCustomers();
        log.info("Initial detection run complete: {} alert(s) created or updated.", alertsCreatedOrUpdated);
    }

    private int loadCustomers() throws IOException {
        int rowsLoaded = 0;
        try (BufferedReader reader = openClasspathCsv("data/customers.csv")) {
            String headerLine = reader.readLine();
            String dataLine;
            while ((dataLine = reader.readLine()) != null) {
                if (dataLine.isBlank()) {
                    continue;
                }
                try {
                    String[] columns = dataLine.split(",", -1);
                    Customer customer = Customer.builder()
                            .customerId(columns[0])
                            .fullName(columns[1] + " " + columns[2])
                            .kycStatus(columns[19])
                            .riskRating(columns[20])
                            .build();
                    customerRepository.save(customer);
                    rowsLoaded++;
                } catch (RuntimeException malformedRowException) {
                    log.error("Skipping malformed customer row [{}]: {}", dataLine, malformedRowException.getMessage());
                }
            }
        }
        return rowsLoaded;
    }

    private int loadAccounts() throws IOException {
        int rowsLoaded = 0;
        try (BufferedReader reader = openClasspathCsv("data/accounts.csv")) {
            String headerLine = reader.readLine();
            String dataLine;
            while ((dataLine = reader.readLine()) != null) {
                if (dataLine.isBlank()) {
                    continue;
                }
                try {
                    String[] columns = dataLine.split(",", -1);
                    String accountId = columns[0];
                    String customerId = columns[1];

                    Customer customer = customerRepository.findById(customerId)
                            .orElseThrow(() -> new IllegalStateException("Unknown customerId " + customerId));

                    Account account = Account.builder()
                            .accountId(accountId)
                            .customer(customer)
                            .accountType(columns[2])
                            .currencyCode(columns[4])
                            .build();
                    accountRepository.save(account);
                    rowsLoaded++;
                } catch (RuntimeException malformedRowException) {
                    log.error("Skipping malformed account row [{}]: {}", dataLine, malformedRowException.getMessage());
                }
            }
        }
        return rowsLoaded;
    }

    private int loadTransactions() throws IOException {
        int rowsLoaded = 0;
        try (BufferedReader reader = openClasspathCsv("data/transactions.csv")) {
            String headerLine = reader.readLine();
            String dataLine;
            while ((dataLine = reader.readLine()) != null) {
                if (dataLine.isBlank()) {
                    continue;
                }
                try {
                    String[] columns = dataLine.split(",", -1);
                    String accountId = columns[1];

                    Account account = accountRepository.findById(accountId)
                            .orElseThrow(() -> new IllegalStateException("Unknown accountId " + accountId));

                    Transaction transaction = Transaction.builder()
                            .transactionId(columns[0])
                            .account(account)
                            .amount(new BigDecimal(columns[2]))
                            .currencyCode(columns[3])
                            .counterpartyCountryCode(columns[4])
                            .channel(columns[5])
                            .transactionTimestamp(Instant.parse(columns[6]))
                            .build();
                    transactionRepository.save(transaction);
                    rowsLoaded++;
                } catch (RuntimeException malformedRowException) {
                    log.error("Skipping malformed transaction row [{}]: {}", dataLine, malformedRowException.getMessage());
                }
            }
        }
        return rowsLoaded;
    }

    private BufferedReader openClasspathCsv(String classpathLocation) throws IOException {
        return new BufferedReader(new InputStreamReader(
                new ClassPathResource(classpathLocation).getInputStream(), StandardCharsets.UTF_8));
    }
}
