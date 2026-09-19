package com.sentinel.aml.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class TransactionIngestRequest {

    @NotBlank
    private String accountId;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal amount;

    @NotBlank
    private String currencyCode;

    @NotBlank
    private String counterpartyCountryCode;

    @NotBlank
    private String channel;

    @NotNull
    private Instant transactionTimestamp;
}
