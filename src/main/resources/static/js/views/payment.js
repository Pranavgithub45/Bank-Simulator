import { api } from '../api.js';
import { toast } from '../toast.js';
import { formatCurrency, formatDate, stampClass, humanizeReason, escapeHtml, displayStatus } from '../utils.js';
import { buildPaymentPayload, generatePrn, getBankConstants } from '../crypto.js';

const stepAmount = document.getElementById('paymentStepAmount');
const stepLogin = document.getElementById('paymentStepLogin');
const stepResult = document.getElementById('paymentStepResult');

const form = document.getElementById('paymentForm');
const amountInput = document.getElementById('amountInput');
const merchantNoteInput = document.getElementById('merchantNote');
const payBtn = document.getElementById('payBtn');

const bankRequestSummary = document.getElementById('bankRequestSummary');
const accountGrid = document.getElementById('accountGrid');
const callbackBehaviorSelect = document.getElementById('callbackBehaviorSelect');

const receiptCard = document.getElementById('receiptCard');
const newPaymentBtn = document.getElementById('newPaymentBtn');

let currentPrn = null;
let currentAmount = null;
const RETURN_URL = 'http://localhost:8082/billdesk/callback-receiver';

function setLoading(btn, loading) {
  btn.disabled = loading;
  btn.querySelector('.btn-label').style.opacity = loading ? '0' : '1';
  btn.querySelector('.spinner').hidden = !loading;
}

function showStep(step) {
  [stepAmount, stepLogin, stepResult].forEach((s) => s.classList.add('hidden'));
  step.classList.remove('hidden');
}

function resetFlow() {
  form.reset();
  callbackBehaviorSelect.value = '';
  currentPrn = null;
  currentAmount = null;
  showStep(stepAmount);
}

// ---------- Step 1: submit encrypted payment request ----------
form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const amount = Number(amountInput.value);
  if (!amount || amount <= 0) {
    toast.error('Enter a valid amount.');
    return;
  }

  setLoading(payBtn, true);
  try {
    const prn = generatePrn();
    const merchantName = merchantNoteInput.value.trim() || 'BillDeskTestMerchantName';

    const payload = await buildPaymentPayload({
      prn,
      amount: amount.toFixed(2),
      merchantName,
      returnUrl: RETURN_URL,
    });

    const response = await api.submitPaymentRequest(payload.mercode, payload.encDhanBankData);

    currentPrn = response.prn;
    currentAmount = amount;
    toast.success(`Payment request accepted — PRN ${response.prn}`);
    await showLoginStep();
  } catch (err) {
    toast.error(`Payment request failed: ${err.message}`);
  } finally {
    setLoading(payBtn, false);
  }
});

// ---------- Step 2: bank login / account selection ----------
async function showLoginStep() {
  const c = await getBankConstants();
  bankRequestSummary.innerHTML = `
    <div><b>PRN</b> ${escapeHtml(currentPrn)}</div>
    <div><b>Amount</b> ${formatCurrency(currentAmount, c.currency)}</div>
    <div><b>Merchant</b> ${escapeHtml(c.mercode)}</div>
    <div><b>Callback URL</b> ${escapeHtml(RETURN_URL)} <span class="muted">(public echo endpoint, for demo purposes)</span></div>
  `;

  accountGrid.innerHTML = '<div class="skeleton-row"></div><div class="skeleton-row"></div>';
  showStep(stepLogin);

  try {
    const accounts = await api.listAccounts();
    if (accounts.length === 0) {
      accountGrid.innerHTML = `<div class="empty-state"><div class="empty-state-title">No test accounts seeded</div></div>`;
      return;
    }
    accountGrid.innerHTML = accounts.map((a) => `
      <div class="account-card" data-account="${escapeHtml(a.accountNo)}">
        <div>
          <div class="account-name">${escapeHtml(a.holderName)}</div>
          <div class="account-no">${escapeHtml(a.accountNo)}</div>
        </div>
        <div class="account-balance">${formatCurrency(a.balance)}</div>
      </div>
    `).join('') + `
      <div class="account-card" data-account="0000000000">
        <div>
          <div class="account-name">Unregistered account</div>
          <div class="account-no">0000000000 (not in bank records)</div>
        </div>
        <div class="account-balance muted">test: invalid account</div>
      </div>
    `;

    accountGrid.querySelectorAll('.account-card').forEach((card) => {
      card.addEventListener('click', () => authorizePayment(card.dataset.account, card));
    });
  } catch (err) {
    toast.error(`Could not load accounts: ${err.message}`);
  }
}

async function authorizePayment(accountNo, cardEl) {
  document.querySelectorAll('.account-card').forEach((c) => c.classList.add('selecting'));
  cardEl.classList.add('selecting');

  try {
    const behavior = callbackBehaviorSelect.value || undefined;
    const result = await api.completePayment(currentPrn, accountNo, behavior);
    renderReceipt(result);
    showStep(stepResult);
  } catch (err) {
    toast.error(`Could not complete payment: ${err.message}`);
    document.querySelectorAll('.account-card').forEach((c) => c.classList.remove('selecting'));
  }
}

// ---------- Step 3: receipt ----------
function renderReceipt(result) {
  const stamp = stampClass(result.status);
  const label = displayStatus(result.status);

  receiptCard.innerHTML = `
    <div class="receipt-title">Dhanlaxmi Bank · Payment ${displayStatus(result.status) === 'SUCCESS' ? 'Receipt' : 'Result'}</div>
    <div class="stamp ${stamp}"><span>${label}</span></div>
    <div class="receipt-amount">${formatCurrency(currentAmount)}</div>
    ${result.failureReason ? `<div class="receipt-reason">${humanizeReason(result.failureReason)}</div>` : ''}
    <div class="receipt-table">
      <div class="receipt-row"><span class="k">PRN</span><span class="v">${escapeHtml(result.prn)}</span></div>
      <div class="receipt-row"><span class="k">Account</span><span class="v">${escapeHtml(result.accountNo)}</span></div>
      <div class="receipt-row"><span class="k">Callback status</span><span class="v">${escapeHtml(result.callbackStatus)}</span></div>
      <div class="receipt-row"><span class="k">Time</span><span class="v">${formatDate(new Date().toISOString())}</span></div>
    </div>
  `;
}

newPaymentBtn.addEventListener('click', resetFlow);

export function renderPayment() {
  resetFlow();
}
