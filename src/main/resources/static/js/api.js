// Thin wrapper around the existing backend endpoints.
// Every function here maps 1:1 to a real controller method already in
// the backend - nothing here invents a new endpoint or changes a
// request/response shape.

const BASE = ''; // same origin - served from src/main/resources/static

async function handle(res) {
  let body = null;
  try { body = await res.json(); } catch (_) { /* no body */ }
  if (!res.ok) {
    const message = (body && (body.message || body.error)) || `Request failed (${res.status})`;
    const err = new Error(message);
    err.status = res.status;
    err.body = body;
    throw err;
  }
  return body;
}

export const api = {
  // ---- Accounts (GET /accounts) ----
  listAccounts: () => fetch(`${BASE}/accounts`).then(handle),

  // ---- Transactions (GET /transactions, GET /transactions/{prn}) ----
  listTransactions: () => fetch(`${BASE}/transactions`).then(handle),
  getTransaction: (prn) => fetch(`${BASE}/transactions/${encodeURIComponent(prn)}`).then(handle),

  // ---- Crypto sandbox (POST /test/checksum, /test/encrypt, /test/decrypt) ----
  checksum: (text) => fetch(`${BASE}/test/checksum`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  }).then(handle),

  encrypt: (text) => fetch(`${BASE}/test/encrypt`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  }).then(handle),

  decrypt: (text) => fetch(`${BASE}/test/decrypt`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ text }),
  }).then(handle),

  getSamplePayload: () => fetch(`${BASE}/test/sample-payment-payload`).then(handle),

  // ---- Real bank contract: payment request (form-urlencoded, exactly as BillDesk sends it) ----
  submitPaymentRequest: (mercode, encDhanBankData) => {
    const form = new URLSearchParams();
    form.set('mercode', mercode);
    form.set('encDhanBankData', encDhanBankData);
    return fetch(`${BASE}/Corporate/prelogin/payment-gateway`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: form.toString(),
    }).then(handle);
  },

  // ---- Complete payment / simulated bank login (JSON body: prn, accountNo, callbackBehavior?) ----
  completePayment: (prn, accountNo, callbackBehavior) => fetch(`${BASE}/bank/complete-payment`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prn, accountNo, callbackBehavior: callbackBehavior || null }),
  }).then(handle),
};
