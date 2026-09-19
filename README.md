# Sentinel AML — Real-Time Money Laundering Detection (Hackathon MVP)

A Spring Boot backend that ingests customer/account/transaction data, runs it through a
configurable rule-based detection engine, and surfaces risk-scored, explainable alerts to
an analyst queue with a disposition (case) workflow. Built to a 60-minute time box — see
**Scope & what was cut** below for what a production build would add on top of this.

## Database schema

Flyway-managed (`src/main/resources/db/migration/V1__init_schema.sql`); Hibernate runs in
`validate` mode, not `update` — the migration script is the single source of truth for schema.
See [`docs/ERD.md`](docs/ERD.md) for the entity-relationship diagram.

> **Upgrading an existing local DB:** if your `sentinel` database already has tables from
> before Flyway was introduced (i.e. from an earlier `ddl-auto: update` run), Flyway will
> refuse to migrate a non-empty schema with no history table. `baseline-on-migrate: true`
> is set so this resolves itself automatically — it only applies when the schema is already
> non-empty, so a fresh/empty database still runs `V1__init_schema.sql` normally.

## Architecture

Clean layered architecture, one direction of dependency (controller → service → repository):

```
controller/   AlertController, IngestController        — REST layer, HTTP concerns only
service/      AlertService, DetectionEngine,            — orchestration + business logic
              TransactionIngestionService, SeedDataLoader
rule/         DetectionRule (interface) + 3 impls       — one class per AML typology
entity/       Customer, Account, Transaction, Alert, Case — JPA entities
repository/   Spring Data JPA repositories               — persistence
config/       HighRiskCountryProperties,                 — externalized, hot-configurable
              DetectionRuleThresholdProperties             rule thresholds (application.yml)
dto/          Request/response DTOs                      — API contracts, decoupled from entities
exception/    GlobalExceptionHandler                     — consistent JSON error responses
```

**Detection engine.** `DetectionRule` is a plain interface; each typology (`LargeTransactionRule`,
`StructuringRule`, `HighRiskJurisdictionRule`) is a `@Component` implementing it. `DetectionEngine`
autowires *every* `DetectionRule` bean as a `List<DetectionRule>` — adding a new typology later is
just adding a new `@Component`, no other code changes. Each rule returns a `RuleEvaluationResult`
carrying a risk score, a human-readable explanation, and the evidence transaction IDs, so every
alert can answer "why was this flagged?" directly from its own data.

**De-duplication.** `AlertService` only creates a new `Alert` if the customer has no existing
`OPEN` alert for that exact rule type; otherwise it merges the new evidence into the existing
alert and bumps its risk score. Alerts are **never deleted** — disposition (`POST
/api/v1/alerts/{id}/disposition`) flips status to `DISPOSITIONED` and writes a `Case` row with
the analyst's reason and identity, satisfying the audit requirement.

## Rule configuration (no redeploy needed)

All thresholds live in `application.yml` under `sentinel.*` and are bound via
`@ConfigurationProperties` (`HighRiskCountryProperties`, `DetectionRuleThresholdProperties`).
Tune the CTR threshold, structuring band/window, or the high-risk country list by editing the
YAML (or overriding with env vars / a mounted config file) — no code change or rebuild required.

```yaml
sentinel:
  high-risk-countries: [IR, KP, SY, CU, RU]
  rules:
    large-transaction:
      threshold: 10000
    structuring:
      min-amount: 9000
      max-amount: 9999
      min-count: 3
      window-hours: 24
```

## Running it

Requires Java 17+ and a local PostgreSQL instance.

```bash
createdb sentinel   # or: psql -U postgres -c "CREATE DATABASE sentinel;"

export DB_NAME=sentinel
export DB_USERNAME=postgres
export DB_PASSWORD=postgres   # never hardcoded — read from env vars in application.yml

mvn spring-boot:run
```

On first startup, `SeedDataLoader` loads `src/main/resources/data/{customers,accounts,transactions}.csv`
into Postgres (skipping any malformed row with a logged error rather than failing the whole load)
and immediately runs one detection pass, so alerts already exist the moment the app is up.

> **Note on seed data:** `customers.csv` and `accounts.csv` are used exactly as provided (2
> customers, 2 accounts, both accounts under `CUST_00001`) — nothing added or modified.
> `transactions.csv` is newly authored (none existed) with 9 rows on those two real accounts,
> deliberately engineered to trip all three typologies (a $15,000 transaction, a small transfer
> to a high-risk jurisdiction, and 3 transactions in the $9,000-$9,999 structuring band within
> 24h) plus enough normal transactions that the alerts are a visible minority, not "everything
> gets flagged."

## API documentation (OpenAPI/Swagger)

Auto-generated from the controllers/DTOs via `springdoc-openapi` — no hand-written spec to keep
in sync:

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Raw OpenAPI 3 JSON: http://localhost:8080/v3/api-docs

## Demo walkthrough

```bash
# 1. Analyst queue, highest risk first — 3 alerts, one per typology
curl -s http://localhost:8080/api/v1/alerts | jq

# 2. Full detail on one alert — rule, risk score, explanation, evidence transaction IDs
curl -s http://localhost:8080/api/v1/alerts/1 | jq

# 3. Analyst clears it — never deletes the alert, just records disposition + who + why
curl -s -X POST http://localhost:8080/api/v1/alerts/1/disposition \
  -H "Content-Type: application/json" \
  -d '{"dispositionStatus":"CLEARED_FALSE_POSITIVE","dispositionReason":"KYC confirmed source of funds.","analystId":"analyst.rao"}' | jq

# 4. Incremental/streaming ingestion of a new transaction — re-evaluates just that
#    customer and merges into an existing OPEN alert instead of creating a duplicate
curl -s -X POST http://localhost:8080/api/v1/ingest/transactions \
  -H "Content-Type: application/json" \
  -d '[{"accountId":"ACC_000001","amount":500.00,"currencyCode":"INR","counterpartyCountryCode":"IR","channel":"WIRE_TRANSFER","transactionTimestamp":"2026-09-19T08:00:00Z"}]' | jq
```

## Tests

```bash
mvn test
```

All three detection rules have unit test coverage: `LargeTransactionRuleTest`,
`StructuringRuleTest`, and `HighRiskJurisdictionRuleTest`. Each covers the trigger case and at
least one meaningful non-trigger edge case — e.g. three $8,999 transactions must **not** trigger
structuring since the band is $9,000–$9,999, and a non-sanctioned-country transaction must not
trigger the jurisdiction rule regardless of amount.

## Scope & what was deliberately cut

The full problem statement is a multi-day build (Kafka streaming, Drools, ML anomaly scoring,
RBAC, PII masking, multi-currency FX normalization, a frontend dashboard). Under a 60-minute
box, these were consciously deprioritized rather than missed — listed here so it's clear what a
follow-up pass would add:

- **Kafka / Spring Cloud Stream streaming ingestion** — `POST /api/v1/ingest/transactions`
  covers the same functional need (single or batched transactions, immediate re-detection)
  without the operational overhead of standing up a broker for a demo.
- **Drools rule engine** — the plain-Java `DetectionRule` interface is faster to write, equally
  explainable, and just as swappable for Drools later if rule authoring needs to move to
  compliance analysts rather than engineers.
- **Rapid Movement of Funds** and **Behavioral Deviation (3x 90-day rolling average)** rules —
  both need materialized historical baselines/rolling windows that are a bigger lift than the
  time box allowed. The 3 shipped rules (large-transaction/CTR, structuring, high-risk
  jurisdiction) are the cheapest-to-build, highest-signal typologies from the business rules list.
- **Spring Security / RBAC** — no auth layer; all endpoints are open. PII masking in list views
  is likewise skipped.
- **Multi-currency FX normalization** — thresholds are applied to each transaction's native
  amount directly rather than a normalized base currency; the seed data is INR-only so this
  doesn't distort the demo, but it's a real gap versus business rule #9.
- **SAR draft generation, frontend dashboard** — not built. (Flyway migrations, an ERD, and
  OpenAPI/Swagger documentation *are* included — see the sections above.)

## Known limitation

Detection re-evaluates a customer's *entire* transaction history on every affected ingest call
(scoped to only the customer(s) in that ingest batch, not the whole customer base). If an alert
for a given customer+rule has already been dispositioned and a *new* transaction for that same
customer causes the rule to re-evaluate true again, a fresh alert is created rather than the
closed one reopening — there's no evidence-level dedup that spans a disposition boundary. Fine
for a hackathon demo's data volumes; a production version would want per-transaction incremental
evaluation instead of full-history re-scans.
