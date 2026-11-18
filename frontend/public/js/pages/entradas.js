import { api, session } from '../api.js';
import { html, raw, fmt, aviso, toast, qrSvg, acts, forms, onLeave } from '../ui.js';

let S;   // estado de la compra en curso
const precio = (zona, tipo) => S.precios.find(p => p.zona === zona && p.tipoEntrada === tipo)?.precio ?? 0;
const unit = (a) => +(precio(a.zona, S.tipo) * (1 - S.descuento / 100)).toFixed(2);
const seleccion = () => S.asientos.filter(a => S.sel.has(a.asientoId));

export default async function ({ id }) {
  const [p, precios, asientos] = await Promise.all([api.partido(id), api.precios(id), api.asientos(id)]);
  const socio = session.user ? await api.socio(session.user.usuarioId).catch(() => null) : null;
  S = { id, p, precios, asientos, sel: new Set(), tipo: 'general', socio, descuento: socio?.estado === 'activo' ? socio.descuentoPorcentaje : 0, reserva: null, entradas: null };
  const zonas = [...new Set(asientos.map(a => a.zona))].sort();
  const sinVenta = !p.ventaEntradasAbierta;
  return { html: html`<p><a href="#/partidos/${id}">← ${p.rival}</a></p>
    <div class="cab"><h1>Entradas</h1><span>${p.esLocal ? 'vs' : '@'} <b>${p.rival}</b> · ${fmt.diaLargo(p.fechaHora)}, ${fmt.hora(p.fechaHora)}</span></div>
    ${sinVenta ? aviso('La venta de entradas para este partido no está abierta.', 'warn') : ''}
    <div class="entradas-grid"><div class="pista-mapa"><div class="cancha">Pista</div>
      ${zonas.map(z => html`<div class="zona"><h3>Zona ${z}</h3><div id="zona-${z}">${mapaZona(z)}</div></div>`)}
      <div class="leyenda"><span><i></i>Libre</span><span><i style="background:var(--ambar);border-color:var(--ambar-osc)"></i>Tu selección</span><span><i style="background:#C5CED9;border-color:#C5CED9"></i>Reservado</span><span><i style="background:#8896A6;border-color:#8896A6"></i>Ocupado</span></div></div>
      <aside class="resumen" id="panel" aria-live="polite">${panel()}</aside></div>` };
}

function mapaZona(z) {
  const filas = Map.groupBy(S.asientos.filter(a => a.zona === z), a => a.fila);
  return [...filas].sort((a, b) => a[0] - b[0]).map(([f, as]) => html`<div class="fila-as"><span>${f}</span>${as.sort((a, b) => a.numero - b.numero).map(a =>
    html`<button type="button" class="seat ${S.sel.has(a.asientoId) ? 'sel' : ''}" data-act="seat" data-id="${a.asientoId}" data-e="${a.estado}" ${a.estado !== 'libre' ? 'disabled' : ''} aria-label="Zona ${a.zona}, fila ${a.fila}, asiento ${a.numero}, ${a.estado}"></button>`)}</div>`);
}
const repintar = () => { document.getElementById('panel').innerHTML = panel().s; };

function panel() {
  if (S.entradas) return html`<h2>¡Entradas listas!</h2><p class="suave">Presenta el código QR en el acceso al pabellón. También las encontrarás en <a href="#/cuenta/entradas">Mi cuenta</a>.</p>${S.entradas.map(e => ticket(e))}<a class="btn sec" href="#/partidos">Volver a partidos</a>`;
  if (S.reserva) {
    const total = seleccion().reduce((s, a) => s + unit(a), 0);
    return html`<h2>Pagar</h2><div class="aviso warn" style="margin:10px 0">Asientos retenidos durante <b class="num" id="atras"></b></div>
      <dl>${seleccion().map(a => html`<div><dt>Zona ${a.zona} · F${a.fila} · A${a.numero}</dt><dd>${fmt.euros(unit(a))}</dd></div>`)}</dl>
      <div class="total">${fmt.euros(total)}</div>${S.descuento ? html`<p class="peq suave">Incluye tu descuento de socio del ${S.descuento} %.</p>` : ''}
      <form data-form="comprar" style="margin-top:14px"><fieldset style="border:0;padding:0;margin:0 0 14px"><legend class="peq" style="font-weight:600;margin-bottom:6px">Método de pago</legend>
        ${[['tarjeta', 'Tarjeta'], ['bizum', 'Bizum'], ['paypal', 'PayPal']].map(([v, t], i) => html`<label class="chk"><input type="radio" name="metodoPago" value="${v}" ${i === 0 ? 'checked' : ''}> ${t}</label>`)}</fieldset>
        <div class="err"></div><button class="btn compra" style="width:100%" type="submit">Pagar ${fmt.euros(total)}</button></form>
      <button class="btn sec" style="width:100%;margin-top:8px" data-act="cancelar-reserva">Cancelar reserva</button>`;
  }
  const sel = seleccion(), total = sel.reduce((s, a) => s + unit(a), 0), esSocio = S.socio?.estado === 'activo';
  return html`<h2>Tu selección</h2>
    <div class="campo" style="margin-top:12px"><label for="tipo">Tipo de entrada</label><select id="tipo"><option value="general" ${S.tipo === 'general' ? 'selected' : ''}>General</option><option value="vip" ${S.tipo === 'vip' ? 'selected' : ''}>VIP</option><option value="socio" ${S.tipo === 'socio' ? 'selected' : ''} ${esSocio ? '' : 'disabled'}>Socio${esSocio ? '' : ' (solo socios activos)'}</option></select></div>
    ${sel.length ? html`<dl>${sel.map(a => html`<div><dt>Zona ${a.zona} · F${a.fila} · A${a.numero}</dt><dd>${fmt.euros(unit(a))}</dd></div>`)}</dl><div class="total">${fmt.euros(total)}</div>${S.descuento ? html`<p class="peq suave">Descuento de socio del ${S.descuento} % incluido.</p>` : ''}`
      : html`<p class="suave" style="margin:14px 0">Elige hasta 10 asientos libres en el mapa.</p>`}
    ${!S.p.ventaEntradasAbierta ? '' : session.user ? html`<button class="btn compra" style="width:100%" data-act="reservar" ${sel.length ? '' : 'disabled'}>Reservar ${sel.length || ''} asiento${sel.length === 1 ? '' : 's'}</button><p class="peq suave" style="margin-top:8px">La reserva dura 10 minutos.</p>`
      : html`<a class="btn compra" style="width:100%" href="#/entrar?volver=${encodeURIComponent('/entradas/' + S.id)}">Entra para reservar</a>`}`;
}

export const ticket = e => html`<div class="entrada"><div class="qr">${qrSvg(e.codigoQr)}</div><div><b>Zona ${e.asiento.zona}, fila ${e.asiento.fila}, asiento ${e.asiento.numero}</b><br><span class="etq ${e.estado}">${e.estado}</span> <span class="peq suave">${e.tipoEntrada} · ${fmt.euros(e.precioPagado)}</span><br><code>${e.codigoQr}</code><div class="peq suave">Entrada ${e.entradaId.slice(0, 8)}</div></div></div>`;

acts.seat = el => {
  const id = el.dataset.id;
  if (S.sel.has(id)) S.sel.delete(id); else if (S.sel.size >= 10) return toast('Máximo 10 asientos por reserva', 'error'); else S.sel.add(id);
  el.classList.toggle('sel', S.sel.has(id)); repintar();
};
document.addEventListener('change', e => { if (e.target.id === 'tipo' && S) { S.tipo = e.target.value; repintar(); } });
acts.reservar = async () => {
  try { S.reserva = await api.reservar({ partidoId: S.id, asientos: [...S.sel] }); }
  catch (e) { S.asientos = await api.asientos(S.id); S.sel = new Set([...S.sel].filter(i => S.asientos.find(a => a.asientoId === i)?.estado === 'libre')); refrescarMapa(); throw e; }
  S.asientos.forEach(a => { if (S.sel.has(a.asientoId)) a.estado = 'reservado'; }); bloquear(true); repintar(); cuentaAtras();
};
acts['cancelar-reserva'] = async () => { await api.cancelarReserva(S.reserva.reservaId); await liberar('Reserva cancelada'); };
forms.comprar = async f => {
  S.entradas = await api.comprar({ reservaId: S.reserva.reservaId, tipoEntrada: S.tipo, metodoPago: new FormData(f).get('metodoPago') });
  clearInterval(S.timer); repintar(); toast('Compra completada');
};
async function liberar(msg) {
  clearInterval(S.timer); S.reserva = null; S.sel = new Set(); S.asientos = await api.asientos(S.id); refrescarMapa(); repintar(); msg && toast(msg);
}
function refrescarMapa() { [...new Set(S.asientos.map(a => a.zona))].forEach(z => { const el = document.getElementById('zona-' + z); if (el) el.innerHTML = mapaZona(z).map(String).join(''); }); }
function bloquear(on) { document.querySelectorAll('.seat.sel').forEach(b => b.disabled = on); }
function cuentaAtras() {
  const t = () => { const s = Math.max(0, Math.round((new Date(S.reserva.expiraEn) - Date.now()) / 1000)), el = document.getElementById('atras'); if (el) el.textContent = fmt.min(s); if (s === 0) { liberar('La reserva ha caducado'); } };
  t(); S.timer = setInterval(t, 1000); onLeave(() => clearInterval(S.timer));
}
