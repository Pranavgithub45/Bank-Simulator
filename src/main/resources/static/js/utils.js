export function formatCurrency(amount, currency = 'INR') {
  const n = Number(amount);
  if (Number.isNaN(n)) return amount;
  const symbol = currency === 'INR' ? '₹' : currency + ' ';
  return symbol + n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function formatDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

export function statusBadgeClass(status) {
  switch (status) {
    case 'SUCCESS': return 'badge-success';
    case 'FAILURE': return 'badge-failure';
    case 'RECEIVED': return 'badge-received';
    case 'SENT': return 'badge-success';
    case 'FAILED': return 'badge-failure';
    case 'DROPPED': return 'badge-failure';
    case 'NOT_SENT': return 'badge-neutral';
    default: return 'badge-neutral';
  }
}

export function stampClass(status) {
  switch (status) {
    case 'SUCCESS': return 'stamp-success';
    case 'FAILURE': return 'stamp-failure';
    default: return 'stamp-received';
  }
}

export function humanizeReason(reason) {
  if (!reason) return '';
  return reason.replace(/_/g, ' ').replace(/\w\S*/g, (t) => t[0] + t.slice(1).toLowerCase());
}

export function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function el(html) {
  const template = document.createElement('template');
  template.innerHTML = html.trim();
  return template.content.firstElementChild;
}

export function displayStatus(status) {
  const normalized = String(status || '').toUpperCase();
  return ['SUCCESS', 'FAILURE', 'RECEIVED'].includes(normalized) ? normalized : 'PROCESSING';
}
