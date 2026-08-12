# Dhanlaxmi Bank Simulator — All Phases (1 through 6)

Backend-only simulator for BillDesk ↔ Dhanlaxmi Bank integration testing.
Complete: project setup, DB + seeded accounts, checksum/AES crypto core,
payment request flow with generic account/balance checks, the callback
engine, and Double Verification.

See `TESTING.md` for full step-by-step testing instructions.

## Endpoints

| Purpose | Method & Path | Notes |
|---|---|---|
| Payment request (BillDesk → Bank) | `POST /Corporate/prelogin/payment-gateway` | Real bank contract - form-urlencoded `mercode` + `encDhanBankData`, unchanged |
| Complete payment ("customer logs in") | `POST /bank/complete-payment` | JSON body: `{ "prn", "accountNo", "callbackBehavior"? }` - **not** in the URL |
| Double Verification (BillDesk → Bank) | `POST /Corporate/prelogin/payment-gateway/paymentDoubleVerification` | Real bank contract - JSON body, read-only lookup |
| List / inspect accounts, transactions | `GET /accounts`, `GET /transactions`, `GET /transactions/{prn}` | Testing convenience, not part of the bank contract |
| Crypto sandbox | `POST /test/checksum`, `/test/encrypt`, `/test/decrypt`, `GET /test/sample-payment-payload` | Testing convenience |
| Mock BillDesk callback receiver | `POST /billdesk/callback-receiver` | Testing convenience - a reachable stand-in for BillDesk's own callback URL, so `RU` has somewhere real to deliver to. Set via `dhanbank.default-return-url`. |

### Why `/bank/complete-payment` is a separate endpoint, not merged into the payment request

They represent two different real-world actors, not two halves of the same
API call. `POST /Corporate/prelogin/payment-gateway` is the actual
documented bank contract — BillDesk calls it server-to-server with just
`mercode` + `encDhanBankData`. `POST /bank/complete-payment` stands in for
the customer's browser sitting on the bank's login/authorization page
(spec sections 23-24) — in production this would be an HTML form
submission, not a BillDesk API call at all. Since this project doesn't
build that login page, this endpoint exists purely as a way to trigger
that step without one. Merging them would break the real contract, since
BillDesk never sends an account number in its request.

### Why `prn` / `accountNo` moved into the request body

Originally `accountNo` was a query parameter and `prn` a path variable.
Both now travel in the JSON body instead — URL components (path and query
string) end up in server access logs, proxy logs, and browser history;
the body doesn't.

## What's included, by phase

1. **Setup** — Spring Boot 3, Java 17, Maven, `BankProperties` config
2. **Database** — H2 file-mode, `BankAccount` + `Transaction` entities,
   `DataSeeder` (4 accounts, tagged only where genuinely needed)
3. **Crypto core** — `ChecksumService` (SHA-512), `EncryptionService`
   (AES-256, hex in/out), `ParamCodec`
4. **Payment flow** — `PaymentService.processPaymentRequest()` (mercode →
   decrypt → checksum → duplicate check → create transaction) and
   `completePayment()`, where invalid account and insufficient balance
   are computed **generically** for every transaction rather than tied to
   a specific seeded account
5. **Callback engine** — `CallbackService` builds the encrypted response
   and delivers it per `CallbackBehavior` (NORMAL / DELAY / DROP /
   DUPLICATE). Sent automatically once `complete-payment` resolves a
   transaction (skipped for PENDING, per spec). TIMEOUT-tagged accounts
   automatically get DELAY unless overridden in the request body.
6. **Double Verification** — `DoubleVerificationController`, a read-only
   lookup that never creates or changes a transaction, reporting
   `statusCode`/`statusDescription`/encrypted `verificationResponse`

## Quick start

```bash
mvn clean install
mvn test              # all 5 test classes
mvn spring-boot:run   # app starts on http://localhost:8082
```

Then follow `TESTING.md` for the complete walkthrough.

## Configuration

Everything bank-specific is in `src/main/resources/application.yml` under
`dhanbank:` — merchant code, bank ID, currency, the AES key (kept openly
in config as agreed, since this is a test simulator),
`callback-delay-seconds`, and `default-return-url`.

## Fixed: callback status always FAILED

`CallbackServiceImpl`'s SENT/FAILED decision was always driven by a real
try/catch around the actual HTTP call - never hardcoded - but the sample
payload's `RU` pointed at `http://localhost:8081/callback`, a port
nothing in this project ever listened on, so every delivery attempt threw
a connection exception and correctly (if unhelpfully) landed on `FAILED`
regardless of the payment's own outcome. Fixed by adding a real,
always-reachable mock BillDesk receiver
(`BillDeskCallbackReceiverController`, `POST /billdesk/callback-receiver`)
and pointing the default `RU` (`dhanbank.default-return-url`) at it. See
`CallbackFlowIntegrationTest` and the "Bug fix" section of `TESTING.md`.

## Open item

The AES cipher mode (`dhanbank.cipher-transformation`, currently
`AES/ECB/PKCS5Padding`) is a documented assumption, not a confirmed fact —
the bank spec never states an IV, and the sample values in the provided
doc aren't internally consistent enough to derive the real mode from them.
If a real reference test vector becomes available later, only
`AesEncryptionService` and this one config value need to change.
