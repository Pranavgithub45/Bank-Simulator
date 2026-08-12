# Testing Guide — All Phases

## Bug fix: callback status always FAILED

Previously, `/test/sample-payment-payload` (and the test fixtures) hard-coded
`RU=http://localhost:8081/callback` - a port nothing in this project ever
listened on. `CallbackServiceImpl`'s SENT/FAILED logic itself was always
correct (a real try/catch around the actual HTTP call, never hardcoded),
but every delivery attempt against that dead URL threw a connection
exception and was accordingly marked `FAILED`, no matter how the payment
itself resolved.

Fix: this app now has its own built-in, always-reachable mock BillDesk
callback receiver (`POST /billdesk/callback-receiver`,
`BillDeskCallbackReceiverController`), and the default `RU` used by the
sample payload (`dhanbank.default-return-url` in `application.yml`) points
at it. A `SUCCESS` payment with a `NORMAL` callback now correctly ends up
`callbackStatus: SENT`. See `CallbackFlowIntegrationTest` for an automated
regression test of this exact scenario (and that `DROP` still behaves
correctly afterward).

## Prerequisites

- Java 17+ (`java -version`)
- Maven (`mvn -version`)
- `curl` or Postman

## Step 1 — Build and test

```bash
unzip dhanlaxmi-bank-simulator.zip
cd dhanlaxmi-bank-simulator
mvn clean install
mvn test
```

Five test classes run:

- **`CryptoCoreTests`** (Phase 3) — AES round trip, checksum
  determinism/tamper detection, param codec, full pipeline.
- **`PaymentFlowTests`** (Phase 4) — full payment flow: success, generic
  invalid account, generic insufficient balance, the same low-balance
  account succeeding when the amount fits, generic failure, pending,
  duplicate PRN, wrong mercode, tampered checksum, double-completion,
  unknown PRN.
- **`CallbackServiceTests`** (Phase 5) — DROP never attempts delivery,
  NORMAL against an unreachable URL ends up FAILED. No real network
  calls are made (deliberately bad/unreachable URLs).
- **`CallbackFlowIntegrationTest`** (callback-status bug fix) — drives the
  full stack over real HTTP on a random port: a SUCCESS payment with a
  NORMAL callback against the app's own mock BillDesk receiver ends up
  `callbackStatus: SENT`; DROP still ends up `DROPPED` afterward.
- **`DoubleVerificationTests`** (Phase 6) — resolved SUCCESS transaction
  returns `statusCode 000`, unknown PRN returns `004`, wrong mercode
  returns `001`, tampered checksum returns `003`, a PENDING transaction
  is reported as "Pending".

All should pass. If any fail, stop here before testing manually.

## Step 2 — Start the app

```bash
mvn spring-boot:run
```

Runs on **http://localhost:8082**. First run seeds 4 accounts.

## Step 3 — Confirm seeded accounts

```bash
curl http://localhost:8082/accounts
```

| account_no | holder_name | balance | scenario_tag |
|---|---|---|---|
| 1111000011 | Test Success User | 50000.00 | SUCCESS |
| 2222000022 | Test Low Balance User | 100.00 | SUCCESS |
| 4444000044 | Test Timeout User | 50000.00 | TIMEOUT |
| 5555000055 | Test Generic Failure User | 50000.00 | GENERIC_FAILURE |

Any account number *not* in this table → generic `INVALID_ACCOUNT`. Any
amount exceeding an account's real balance → generic `INSUFFICIENT_BALANCE`.

## Step 4 — Send a payment request

Get a ready-made encrypted payload:
```bash
curl http://localhost:8082/test/sample-payment-payload
```
Copy `encDhanBankData` (uses `PRN=BLDK000123`, `AMT=221.01` - each PRN can
only be used once, so build a fresh one via `/test/checksum` + `/test/encrypt`
for repeat tests, following the same field order).

```bash
curl -X POST http://localhost:8082/Corporate/prelogin/payment-gateway \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "mercode=BLDKPG001C" \
  --data-urlencode "encDhanBankData=<paste here>"
```

Expected: `{"prn": "BLDK000123", "bid": "...", "status": "RECEIVED", "message": "..."}`

Rejection cases: wrong `mercode` → `400 INVALID_MERCODE`; tampered
checksum → `403 INVALID_CHECKSUM`; same request sent twice → `409
DUPLICATE_TRANSACTION` on the second call.

## Step 5 — Complete the payment (JSON body, not URL)

**Successful payment, normal callback:**
```bash
curl -X POST http://localhost:8082/bank/complete-payment \
  -H "Content-Type: application/json" \
  -d '{"prn": "BLDK000123", "accountNo": "1111000011"}'
```
Expected: `"status": "SUCCESS"`, `"callbackStatus": "SENT"`.

The payload from `/test/sample-payment-payload` now uses
`dhanbank.default-return-url` as its `RU`, which points at this app's own
built-in mock BillDesk receiver (`POST /billdesk/callback-receiver`) - so
as long as the simulator itself is running, the callback has somewhere
real to land and `callbackStatus` comes back `SENT`, not `FAILED`.
`callbackStatus` only becomes `FAILED` if the delivery attempt genuinely
fails - e.g. point a transaction's `RU` at a URL nothing is listening on
(the old default, `http://localhost:8081/callback`, still works as a
deliberate failure case if you want to see `FAILED` on purpose).

**Generic invalid account** (any PRN + any unregistered account number):
```bash
curl -X POST http://localhost:8082/bank/complete-payment \
  -H "Content-Type: application/json" \
  -d '{"prn": "<some-other-prn>", "accountNo": "0000000000"}'
```
Expected: `"status": "FAILURE"`, `"failureReason": "INVALID_ACCOUNT"`

**Generic insufficient balance** (send a request with `AMT` over `100.00`
through Step 4 first, using account `2222000022`):
```bash
curl -X POST http://localhost:8082/bank/complete-payment \
  -H "Content-Type: application/json" \
  -d '{"prn": "<that-prn>", "accountNo": "2222000022"}'
```
Expected: `"status": "FAILURE"`, `"failureReason": "INSUFFICIENT_BALANCE"`

Same account, a fresh PRN with `AMT` under `100.00` → `"status": "SUCCESS"`
— proves the check is per-transaction, not a fixed account label.

**Generic transaction failure:**
```bash
curl -X POST http://localhost:8082/bank/complete-payment \
  -H "Content-Type: application/json" \
  -d '{"prn": "<a-prn>", "accountNo": "5555000055"}'
```
Expected: `"status": "FAILURE"`, `"failureReason": "TRANSACTION_FAILED"`

**Pending (no callback sent at all):**
```bash
curl -X POST http://localhost:8082/bank/complete-payment \
  -H "Content-Type: application/json" \
```
Expected: `"status": "PENDING"`, `"callbackStatus": "NOT_SENT"`

**Already resolved:** call complete-payment again for a PRN you already
resolved → `409 ALREADY_RESOLVED`.

## Step 6 — Callback behaviors (Phase 5)

**Forced DROP** (this is what makes Double Verification necessary):
```bash
curl -X POST http://localhost:8082/bank/complete-payment \
  -H "Content-Type: application/json" \
  -d '{"prn": "<a-prn>", "accountNo": "1111000011", "callbackBehavior": "DROP"}'
```
Expected: `"callbackStatus": "DROPPED"` — no HTTP call is even attempted.

**Automatic DELAY for TIMEOUT-tagged accounts** — complete a payment with
account `4444000044` and no `callbackBehavior` override. The request will
block for `dhanbank.callback-delay-seconds` (5s by default) before
responding, then behave like NORMAL. You can also force `DELAY` on any
account explicitly the same way as DROP above.

**DUPLICATE** — set `"callbackBehavior": "DUPLICATE"` — the callback is
attempted twice.

## Step 7 — Double Verification (Phase 6)

Build a DV request (JSON, `MD=V` instead of `MD=P`, includes `STATFLG`
and `BID`), checksum it, encrypt it — same pattern as the payment request
but with the DV-specific field set (see spec section 36, or
`DoubleVerificationTests.buildEncryptedDvRequest` in the test code for a
working example).

```bash
curl -X POST http://localhost:8082/Corporate/prelogin/payment-gateway/paymentDoubleVerification \
  -H "Content-Type: application/json" \
  -d '{"mercode": "BLDKPG001C", "encDhanBankData": "<encrypted DV payload>"}'
```

Expected for a resolved SUCCESS transaction:
```json
{
  "statusCode": "000",
  "statusDescription": "Success",
  "verificationResponse": "<encrypted payload>"
}
```
Decrypt `verificationResponse` via `/test/decrypt` to confirm it contains
`PAID=Y` and the correct `PRN`/`BID`.

Other cases: unknown PRN → `statusCode "004"`; wrong mercode → `"001"`;
tampered checksum → `"003"`; a still-PENDING transaction → `"001"` /
`"Pending"`.

**The realistic end-to-end scenario:** complete a payment with
`callbackBehavior: DROP`, confirm `GET /transactions/{prn}` shows
`callbackStatus: DROPPED`, then call Double Verification for that same
PRN and confirm it correctly reports the real (already-decided) status
even though BillDesk never got the callback.

## Step 8 — Inspect final state anytime

```bash
curl http://localhost:8082/transactions
curl http://localhost:8082/transactions/<prn>
```

## Reminder: the AES mode open item

Still `AES/ECB/PKCS5Padding` by default, still unconfirmed against a real
bank reference — see README for details.
