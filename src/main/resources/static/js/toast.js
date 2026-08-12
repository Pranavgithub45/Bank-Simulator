const host = document.getElementById('toastHost');

function show(message, type = 'info', timeout = 4200) {
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerHTML = `<span class="toast-msg"></span>`;
  toast.querySelector('.toast-msg').textContent = message;
  host.appendChild(toast);

  const remove = () => {
    toast.classList.add('leaving');
    setTimeout(() => toast.remove(), 200);
  };
  setTimeout(remove, timeout);
  toast.addEventListener('click', remove);
}

export const toast = {
  success: (msg) => show(msg, 'success'),
  error: (msg) => show(msg, 'error'),
  info: (msg) => show(msg, 'info'),
};
