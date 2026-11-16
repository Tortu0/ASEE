import { api, session, ApiError } from './api.js';
import { html, esc, raw, cargando, aviso, toast, cerrarModal, acts, forms, cleanups } from './ui.js';

const ROUTES = [
  ['/', 'home'], ['/partidos', 'partidos'], ['/partidos/:id', 'partidos'], ['/entradas/:id', 'entradas'], ['/plantilla', 'plantilla'], ['/jugadores/:id', 'plantilla'],
  ['/noticias', 'noticias'], ['/noticias/:id', 'noticias'], ['/estadisticas', 'estadisticas'], ['/tienda', 'tienda'], ['/carrito', 'tienda', 'auth'], ['/club', 'club'],
  ['/entrar', 'cuenta'], ['/cuenta', 'cuenta', 'auth'], ['/cuenta/:tab', 'cuenta', 'auth'], ['/gestion', 'gestion', 'staff'], ['/gestion/:sec', 'gestion', 'staff'], ['/estado', 'estado'],
].map(([p, mod, need]) => ({ mod, need, keys: [...p.matchAll(/:(\w+)/g)].map(m => m[1]), re: new RegExp('^' + p.replace(/:\w+/g, '([^/]+)') + '$'), path: p }));
const NAV = [['/partidos', 'Partidos'], ['/plantilla', 'Plantilla'], ['/noticias', 'Noticias'], ['/estadisticas', 'Estadísticas'], ['/tienda', 'Tienda'], ['/club', 'El club']];
const app = document.getElementById('app');

function shell() {
  const u = session.user;
  document.getElementById('nav').innerHTML = [...NAV, ...(session.staff ? [['/gestion', 'Gestión']] : [])].map(([h, t]) => `<a href="#${h}">${t}</a>`).join('');
  document.getElementById('cuenta').innerHTML = u
    ? `<a class="btn sec chico" href="#/carrito">Carrito</a><a class="btn sec chico" href="#/cuenta">${esc(u.nombre.split(' ')[0])}</a><button class="btn chico" data-act="salir">Salir</button>`
    : `<a class="btn sec chico" href="#/entrar?tab=registro">Crear cuenta</a><a class="btn chico" href="#/entrar">Entrar</a>`;
  marcarNav();
}
function marcarNav() {
  const cur = '/' + (location.hash.slice(2).split(/[/?]/)[0] || '');
  document.querySelectorAll('#nav a').forEach(a => a.getAttribute('href') === '#' + cur ? a.setAttribute('aria-current', 'page') : a.removeAttribute('aria-current'));
}

async function route() {
  cleanups.splice(0).forEach(f => f()); document.getElementById('modal').open && cerrarModal();
  const [path, qs] = (location.hash.slice(1) || '/').split('?');
  let r, params = {};
  for (const x of ROUTES) { const m = path.match(x.re); if (m) { r = x; x.keys.forEach((k, i) => params[k] = decodeURIComponent(m[i + 1])); break; } }
  marcarNav();
  if (!r) { app.innerHTML = aviso('Esta página no existe.', 'error').s; return; }
  if (r.need && !session.user) { location.replace(`#/entrar?volver=${encodeURIComponent(path)}`); return; }
  if (r.need === 'staff' && !session.staff) { app.innerHTML = aviso('Esta zona es solo para el personal del club.', 'warn').s; return; }
  app.innerHTML = cargando().s;
  try {
    const mod = await import(`./pages/${r.mod}.js`);
    const out = await mod.default(params, new URLSearchParams(qs || ''), path);
    if (out && out.html) { app.innerHTML = out.html.s; out.mount?.(app); } else app.innerHTML = String(out ?? '');
  } catch (e) {
    console.error(e);
    app.innerHTML = aviso(e instanceof ApiError ? e.message : `Error inesperado: ${e.message}`, 'error').s;
  }
  window.scrollTo(0, 0); app.focus({ preventScroll: true });
}

document.addEventListener('click', async ev => {
  const el = ev.target.closest('[data-act]'); if (!el) return;
  const f = acts[el.dataset.act]; if (!f) return;
  ev.preventDefault(); el.disabled !== undefined && (el.disabled = true);
  try { await f(el, ev); } catch (e) { toast(e.message, 'error'); } finally { if (el.isConnected && el.disabled !== undefined) el.disabled = false; }
});
document.addEventListener('submit', async ev => {
  const form = ev.target.closest('form[data-form]'); if (!form) return;
  ev.preventDefault(); const b = form.querySelector('[type=submit]'); b && (b.disabled = true);
  try { await forms[form.dataset.form](form, ev); } catch (e) { toast(e.message, 'error'); const box = form.querySelector('.err'); if (box) box.innerHTML = aviso(e.message + (e.detalles?.length ? ': ' + e.detalles.join('; ') : ''), 'error').s; } finally { if (b?.isConnected) b.disabled = false; }
});
acts.cerrar = () => cerrarModal();
acts.salir = () => { session.set(null, null); toast('Sesión cerrada'); location.hash = '#/'; };
session.onChange(() => { shell(); });
window.addEventListener('hashchange', route);

async function tema() {
  try {
    const id = await api.identidad(), c = id.coloresOficiales?.[0];
    if (/^#[0-9a-f]{6}$/i.test(c || '')) document.documentElement.style.setProperty('--azul', c);
    document.getElementById('marca').textContent = id.nombreOficial; document.title = id.nombreOficial; document.getElementById('pie').textContent = `© ${new Date().getFullYear()} ${id.nombreOficial}`;
    window.CLUB = id;
  } catch { window.CLUB = { nombreOficial: 'CB Madroñera' }; }
}
shell(); await tema(); route();
