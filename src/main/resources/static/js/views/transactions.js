import { api } from '../api.js';
import { toast } from '../toast.js';
import { openModal, closeModal } from '../modal.js';
import { formatCurrency, formatDate, statusBadgeClass, humanizeReason, escapeHtml, displayStatus } from '../utils.js';

const tableWrap = document.getElementById('transactionsTable');
const searchInput = document.getElementById('txnSearch');
const statusFilter = document.getElementById('txnStatusFilter');
const refreshBtn = document.getElementById('refreshTxnBtn');

let allTransactions = [];

function renderSkeleton() {
  tableWrap.innerHTML = Array.from({ length: 6 }).map(() => '<div class="skeleton-row"></div>').join('');
}

function renderEmpty(message) {
  tableWrap.innerHTML = `
    <div class="empty-state">
      <div class="empty-state-title">No transactions found</div>
      <div class="empty-state-sub">${escapeHtml(message || 'Try a different search or filter.')}</div>
    </div>`;
}

function applyFilters() {
  const term = searchInput.value.trim().toLowerCase();
  const status = statusFilter.value;

  return allTransactions.filter((t) => {
    const matchesStatus = !status || displayStatus(t.status) === status;
    const matchesTerm = !term
      || (t.prn && t.prn.toLowerCase().includes(term))
      || (t.bid && t.bid.toLowerCase().includes(term))
      || (t.accountNo && t.accountNo.toLowerCase().includes(term));
    return matchesStatus && matchesTerm;
  });
}

function renderTable() {
  const filtered = applyFilters();
  if (filtered.length === 0) return renderEmpty(allTransactions.length ? 'No matches for your search.' : 'No transactions have been created yet.');

  const rows = filtered
    .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    .map((t) => `
      <tr data-prn="${escapeHtml(t.prn)}">
        <td class="mono">${escapeHtml(t.prn)}</td>
        <td class="mono muted">${escapeHtml(t.bid || '—')}</td>
        <td class="mono">${formatCurrency(t.amount, t.currency)}</td>
        <td class="mono">${escapeHtml(t.accountNo || '—')}</td>
        <td><span class="badge ${statusBadgeClass(t.status)}">${displayStatus(t.status)}</span></td>
        <td class="muted">${t.failureReason ? humanizeReason(t.failureReason) : '—'}</td>
        <td><span class="badge ${statusBadgeClass(t.callbackStatus)}">${t.callbackStatus}</span></td>
        <td class="muted">${formatDate(t.createdAt)}</td>
      </tr>
    `).join('');

  tableWrap.innerHTML = `
    <table>
      <thead>
        <tr>
          <th>PRN</th><th>BID</th><th>Amount</th><th>Account</th>
          <th>Status</th><th>Failure Reason</th><th>Callback</th><th>Created</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>`;

  tableWrap.querySelectorAll('tbody tr').forEach((row) => {
    row.addEventListener('click', () => openTransactionModal(row.dataset.prn));
  });
}

export async function openTransactionModal(prn) {
  try {
    const t = await api.getTransaction(prn);
    openModal(`
      <h2 style="font-family: var(--font-display); font-size: 17px; margin: 0 0 4px;">Transaction detail</h2>
      <p style="color: var(--slate); font-size: 12.5px; margin: 0 0 16px;">${escapeHtml(t.prn)}</p>

      <div class="detail-row"><span class="k">Status</span><span class="v"><span class="badge ${statusBadgeClass(t.status)}">${displayStatus(t.status)}</span></span></div>
      ${t.failureReason ? `<div class="detail-row"><span class="k">Failure reason</span><span class="v">${humanizeReason(t.failureReason)}</span></div>` : ''}
      <div class="detail-row"><span class="k">Callback status</span><span class="v"><span class="badge ${statusBadgeClass(t.callbackStatus)}">${t.callbackStatus}</span></span></div>
      <div class="detail-row"><span class="k">PRN</span><span class="v">${escapeHtml(t.prn)}</span></div>
      <div class="detail-row"><span class="k">BID</span><span class="v">${escapeHtml(t.bid || '—')}</span></div>
      <div class="detail-row"><span class="k">Account No.</span><span class="v">${escapeHtml(t.accountNo || '—')}</span></div>
      <div class="detail-row"><span class="k">Merchant</span><span class="v">${escapeHtml(t.merchantName || '—')}</span></div>
      <div class="detail-row"><span class="k">Merchant code</span><span class="v">${escapeHtml(t.merchantCode || '—')}</span></div>
      <div class="detail-row"><span class="k">Amount</span><span class="v">${formatCurrency(t.amount, t.currency)}</span></div>
      <div class="detail-row"><span class="k">Return URL</span><span class="v">${escapeHtml(t.returnUrl || '—')}</span></div>
      <div class="detail-row"><span class="k">Created</span><span class="v">${formatDate(t.createdAt)}</span></div>
      <div class="detail-row"><span class="k">Updated</span><span class="v">${formatDate(t.updatedAt)}</span></div>

      <div class="modal-close-row" style="gap:8px;">
        <button class="btn btn-ghost btn-sm" id="modalCloseBtn">Close</button>
      </div>
    `);
    document.getElementById('modalCloseBtn').addEventListener('click', closeModal);
  } catch (e) {
    toast.error(`Could not load transaction: ${e.message}`);
  }
}

export async function renderTransactions() {
  renderSkeleton();
  try {
    allTransactions = await api.listTransactions();
    renderTable();
  } catch (e) {
    toast.error(`Could not load transactions: ${e.message}`);
    renderEmpty('The transaction list could not be loaded.');
  }
}

searchInput.addEventListener('input', renderTable);
statusFilter.addEventListener('change', renderTable);
refreshBtn.addEventListener('click', renderTransactions);
