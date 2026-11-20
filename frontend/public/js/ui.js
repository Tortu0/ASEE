// Utilidades de interfaz: plantillas con escape automático, formatos, avisos y diálogo.
const MAP = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const esc = s => String(s ?? '').replace(/[&<>"']/g, c => MAP[c]);
class Raw { constructor(s) { this.s = s; } toString() { return this.s; } }
export const raw = s => new Raw(s);
const val = v => v instanceof Raw ? v.s : Array.isArray(v) ? v.map(val).join('') : v === false || v == null ? '' : esc(v);
export const html = (strs, ...vals) => new Raw(strs.reduce((a, s, i) => a + s + (i < vals.length ? val(vals[i]) : ''), ''));

const TZ = 'Europe/Madrid';
export const fmt = {
  dia: iso => new Date(iso).toLocaleDateString('es-ES', { weekday: 'short', day: 'numeric', month: 'short', timeZone: TZ }).replace('.', ''),
  diaLargo: iso => new Date(iso).toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: TZ }),
  hora: iso => new Date(iso).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit', timeZone: TZ }),
  fechaHora: iso => new Date(iso).toLocaleString('es-ES', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: TZ }),
  euros: n => new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(n ?? 0),
  num: (n, d = 1) => n == null ? '–' : new Intl.NumberFormat('es-ES', { maximumFractionDigits: d }).format(n),
  min: seg => seg == null ? '–' : `${Math.floor(seg / 60)}:${String(seg % 60).padStart(2, '0')}`,
  cuenta: iso => { const d = Math.round((new Date(iso) - Date.now()) / 864e5); return d === 0 ? 'hoy' : d === 1 ? 'mañana' : d > 1 ? `en ${d} días` : `hace ${-d} días`; },
};
export const ETQ = { programado: 'Programado', en_juego: 'En juego', finalizado: 'Finalizado', aplazado: 'Aplazado', suspendido: 'Suspendido' };
export const POS = { base: 'Base', escolta: 'Escolta', alero: 'Alero', ala_pivot: 'Ala-pívot', pivot: 'Pívot' };
export const CAT = { fichaje: 'Fichajes', lesion: 'Lesiones', convocatoria: 'Convocatorias', cronica: 'Crónicas', club: 'Club', otra: 'Otras' };

export const cargando = () => html`<div aria-busy="true"><div class="cargando" style="width:40%"></div><div class="cargando"></div><div class="cargando" style="width:80%"></div></div>`;
export const aviso = (msg, tipo = '') => html`<div class="aviso ${tipo}" role="${tipo === 'error' ? 'alert' : 'status'}">${msg}</div>`;
export const vacio = msg => html`<div class="vacio">${msg}</div>`;

export function toast(msg, tipo = '') {
  const t = Object.assign(document.createElement('div'), { className: `toast ${tipo}`, textContent: msg });
  document.getElementById('toasts').append(t); setTimeout(() => t.remove(), tipo === 'error' ? 7000 : 3500);
}
export function modal(contenido) {
  const d = document.getElementById('modal');
  d.innerHTML = `<div><button class="btn sec chico cierra" data-act="cerrar" aria-label="Cerrar">✕</button>${contenido}</div>`;
  if (!d.open) d.showModal(); return d;
}
export const cerrarModal = () => document.getElementById('modal').close();
export const qrSvg = texto => { try { const q = window.qrcode(0, 'M'); q.addData(texto); q.make(); return raw(q.createSvgTag({ cellSize: 4, margin: 0, scalable: true })); } catch { return raw(''); } };
export const formData = f => Object.fromEntries(new FormData(f));

// Registros globales: acciones por clic (data-act), formularios (data-form) y limpieza al cambiar de página.
export const acts = {}, forms = {}, cleanups = [];
export const onLeave = f => cleanups.push(f);
export const volver = () => new URLSearchParams(location.hash.split('?')[1] || '').get('volver');
export const urlSegura = u => typeof u === 'string' && /^(https?:\/\/[^\s"'<>]+|\/(?!\/)[^\s"'<>]*)$/i.test(u.trim()) && !/\.example(\/|$)/i.test(u) ? u.trim() : null;
export const iniciales = n => String(n || '?').split(/\s+/).filter(Boolean).slice(0, 2).map(x => x[0].toUpperCase()).join('');
export const imagen = (url, alt, reserva, clase = '') => {
  const u = urlSegura(url), r = reserva || '';
  return html`<img class="${clase}" src="${u || r}" alt="${alt || ''}" loading="lazy" ${r ? raw(`onerror="this.onerror=null;this.src='${r}'"`) : ''}>`;
};
