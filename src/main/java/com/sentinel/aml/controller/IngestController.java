package com.sentinel.aml.controller;

import com.sentinel.aml.dto.TransactionIngestRequest;
import com.sentinel.aml.service.TransactionIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Incoming transaction ingestion. Accepts either a single transaction (streaming-style, one
 * call per event) or a batch (bulk-load style) through the same endpoint and immediately
 * re-runs detection so alerts are up to date before the response is returned.
 */
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestController {

    private final TransactionIngestionService transactionIngestionService;

    @PostMapping("/transactions")
    public ResponseEntity<Map<String, Integer>> ingestTransactions(
            @Valid @RequestBody List<TransactionIngestRequest> incomingTransactions) {
        int alertsCreatedOrUpdated = transactionIngestionService.ingestAndDetect(incomingTransactions);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("transactionsIngested", incomingTransactions.size(),
                        "alertsCreatedOrUpdated", alertsCreatedOrUpdated));
    }
}
