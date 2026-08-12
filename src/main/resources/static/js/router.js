const routes = {};
const titles = {
  dashboard: 'Dashboard',
  payment: 'Make Payment',
  transactions: 'Transactions',
};

export function registerRoute(name, onEnter) {
  routes[name] = onEnter;
}

function currentRoute() {
  const hash = location.hash.replace('#', '');
  return routes[hash] ? hash : 'dashboard';
}

export function navigate(route) {
  location.hash = route;
}

function render() {
  const route = currentRoute();

  document.querySelectorAll('.view').forEach((v) => v.classList.remove('active'));
  const viewEl = document.getElementById(`view-${route}`);
  if (viewEl) viewEl.classList.add('active');

  document.querySelectorAll('.nav-item').forEach((n) => {
    n.classList.toggle('active', n.dataset.route === route);
  });

  document.getElementById('pageTitle').textContent = titles[route] || route;

  if (routes[route]) routes[route]();

  // close mobile sidebar on navigation
  document.querySelector('.sidebar')?.classList.remove('open');
}

export function startRouter() {
  window.addEventListener('hashchange', render);
  render();
}
