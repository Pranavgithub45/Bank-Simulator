const backdrop = document.getElementById('modalBackdrop');
const box = document.getElementById('modalBox');

export function openModal(html) {
  box.innerHTML = html;
  backdrop.classList.remove('hidden');
}

export function closeModal() {
  backdrop.classList.add('hidden');
  box.innerHTML = '';
}

backdrop.addEventListener('click', (e) => {
  if (e.target === backdrop) closeModal();
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') closeModal();
});
