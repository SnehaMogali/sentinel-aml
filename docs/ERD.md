# Sentinel AML — Entity Relationship Diagram

Mirrors `com.sentinel.aml.entity` and the Flyway schema in
`src/main/resources/db/migration/V1__init_schema.sql`.

```mermaid
erDiagram
    CUSTOMER ||--o{ ACCOUNT : owns
    ACCOUNT ||--o{ TRANSACTION : has
    CUSTOMER ||--o{ ALERT : "flagged for"
    ALERT ||--o| CASE : dispositioned_by
    ALERT ||--o{ ALERT_EVIDENCE_TRANSACTIONS : evidence

    CUSTOMER {
        varchar customer_id PK
        varchar full_name
        varchar kyc_status
        varchar risk_rating
    }
    ACCOUNT {
        varchar account_id PK
        varchar customer_id FK
        varchar account_type
        varchar currency_code
    }
    TRANSACTION {
        varchar transaction_id PK
        varchar account_id FK
        numeric amount
        varchar currency_code
        varchar counterparty_country_code
        varchar channel
        timestamptz transaction_timestamp
    }
    ALERT {
        bigint alert_id PK
        varchar customer_id FK
        varchar triggered_rule_type
        int risk_score
        varchar explanation
        varchar status
        timestamptz created_at
    }
    ALERT_EVIDENCE_TRANSACTIONS {
        bigint alert_id FK
        varchar transaction_id
    }
    CASE {
        bigint case_id PK
        bigint alert_id FK
        varchar disposition_status
        varchar disposition_reason
        varchar analyst_id
        timestamptz dispositioned_at
    }
```

**Notes:**
- `ALERT_EVIDENCE_TRANSACTIONS.transaction_id` is a plain string reference, not a foreign key to
  `TRANSACTION` — an evidence entry just names the transaction ID that contributed to the alert,
  since one physical transaction can legitimately be evidence for more than one alert (e.g. a
  single large transaction that is also part of a structuring window).
- `ALERT`–`CASE` is 1:1 (`cases.alert_id` is unique) — one disposition record per alert, written
  when an analyst acts on it; an alert with no `Case` row is still `OPEN`.
- No foreign key from `TRANSACTION` back to `CUSTOMER` directly — a customer's transactions are
  always reached through their accounts (`CUSTOMER → ACCOUNT → TRANSACTION`).
