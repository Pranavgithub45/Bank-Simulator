import { registerRoute, startRouter } from './router.js';
import { renderDashboard } from './views/dashboard.js';
import { renderPayment } from './views/payment.js';
import { renderTransactions } from './views/transactions.js';
import { getBankConstants } from './crypto.js';

registerRoute('dashboard', renderDashboard);
registerRoute('payment', renderPayment);
registerRoute('transactions', renderTransactions);

startRouter();

document.getElementById('menuToggle')?.addEventListener('click', () => {
  document.querySelector('.sidebar').classList.toggle('open');
});

// live clock in the topbar
function tickClock() {
  const el = document.getElementById('clock');
  if (el) el.textContent = new Date().toLocaleTimeString('en-IN', { hour12: false });
}
tickClock();
setInterval(tickClock, 1000);

// environment indicator in the sidebar, read from the backend's own config
getBankConstants().then((c) => {
  document.getElementById('envMercode').textContent = c.mercode;
  document.getElementById('envBankId').textContent = c.bankId;
}).catch(() => {
  document.getElementById('envMercode').textContent = 'unavailable';
});
