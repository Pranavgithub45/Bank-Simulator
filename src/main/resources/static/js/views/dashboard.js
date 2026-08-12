import { api } from '../api.js';
import { toast } from '../toast.js';
import { formatCurrency, formatDate, statusBadgeClass, escapeHtml, displayStatus } from '../utils.js';

const totalEl = document.getElementById('statTotal');
const successEl = document.getElementById('statSuccess');
const failureEl = document.getElementById('statFailure');
const processingEl = document.getElementById('statProcessing');
const droppedEl = document.getElementById('statDropped');
const callbackFailedEl = document.getElementById('statCallbackFailed');
const recentWrap = document.getElementById('recentTransactions');

function renderSkeleton() {
  recentWrap.innerHTML = Array.from({ length: 4 })
    .map(() => '<div class="skeleton-row"></div>')
    .join('');
}

function renderEmpty() {
  recentWrap.innerHTML = `
    <div class="empty-state">
      <div class="empty-state-title">No transactions yet</div>
      <div class="empty-state-sub">Make your first payment to see it show up here.</div>
    </div>`;
}

function renderTable(transactions) {
  if (transactions.length === 0) return renderEmpty();

  const rows = transactions.slice(0, 8).map((t) => `
    <tr data-prn="${escapeHtml(t.prn)}">
      <td class="mono">${escapeHtml(t.prn)}</td>
      <td>${escapeHtml(t.merchantName || '—')}</td>
      <td class="mono">${formatCurrency(t.amount, t.currency)}</td>
      <td><span class="badge ${statusBadgeClass(t.status)}">${displayStatus(t.status)}</span></td>
      <td><span class="badge ${statusBadgeClass(t.callbackStatus)}">${t.callbackStatus}</span></td>
      <td class="muted">${formatDate(t.createdAt)}</td>
    </tr>
  `).join('');

  recentWrap.innerHTML = `
    <table>
      <thead><tr><th>PRN</th><th>Merchant</th><th>Amount</th><th>Status</th><th>Callback</th><th>Created</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>`;

  recentWrap.querySelectorAll('tbody tr').forEach((row) => {
    row.addEventListener('click', async () => {
      const { openTransactionModal } = await import('./transactions.js');
      openTransactionModal(row.dataset.prn);
    });
  });
}

export async function renderDashboard() {
  renderSkeleton();
  try {
    const transactions = await api.listTransactions();

    const total = transactions.length;
    const success = transactions.filter((t) => t.status === 'SUCCESS').length;
    const failure = transactions.filter((t) => t.status === 'FAILURE').length;
    const processing = transactions.filter((t) => displayStatus(t.status) === 'PROCESSING').length;
    const dropped = transactions.filter((t) => t.callbackStatus === 'DROPPED').length;
    const callbackFailed = transactions.filter((t) => t.callbackStatus === 'FAILED').length;

    totalEl.textContent = total;
    successEl.textContent = success;
    failureEl.textContent = failure;
    processingEl.textContent = processing;
    droppedEl.textContent = dropped;
    callbackFailedEl.textContent = callbackFailed;

    const sorted = [...transactions].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    renderTable(sorted);
  } catch (e) {
    toast.error(`Could not load dashboard: ${e.message}`);
    renderEmpty();
  }
}
