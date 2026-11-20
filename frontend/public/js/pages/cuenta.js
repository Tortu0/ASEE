import { api, session } from '../api.js';
import { html, fmt, vacio, aviso, toast, acts, forms, volver, CAT } from '../ui.js';
import { jugadoresMapa } from '../comun.js';
import { ticket } from './entradas.js';

export default async function (p, qs, path) { return path === '/entrar' ? entrar(qs) : cuenta(p.tab || 'perfil'); }

function entrar(qs) {
  if (session.user) { location.replace('#/cuenta'); return ''; }
  const reg = qs.get('tab') === 'registro';
  return { html: html`<section style="max-width:420px"><h1 style="margin-bottom:16px">${reg ? 'Crear cuenta' : 'Entrar'}</h1>
    ${reg ? html`<form data-form="registro"><div class="campo"><label for="n">Nombre completo</label><input id="n" name="nombre" required autocomplete="name"></div><div class="campo"><label for="e">Email</label><input id="e" name="email" type="email" required autocomplete="email"></div>
      <div class="campo"><label for="p">Contraseña</label><input id="p" name="password" type="password" minlength="8" required autocomplete="new-password"><div class="ayuda">Mínimo 8 caracteres.</div></div><div class="err"></div><button class="btn" type="submit">Crear cuenta</button></form><p style="margin-top:16px">¿Ya tienes cuenta? <a href="#/entrar">Entra</a>.</p>`
    : html`<form data-form="login"><div class="campo"><label for="e">Email</label><input id="e" name="email" type="email" required autocomplete="email"></div><div class="campo"><label for="p">Contraseña</label><input id="p" name="password" type="password" required autocomplete="current-password"></div><div class="err"></div><button class="btn" type="submit">Entrar</button></form><p style="margin-top:16px">¿Aún no tienes cuenta? <a href="#/entrar?tab=registro">Créala</a>.</p>`}</section>` };
}
const destino = () => { const v = volver(); location.hash = v ? '#' + v : '#/cuenta'; };
forms.login = async f => { const r = await api.login(Object.fromEntries(new FormData(f))); session.set(r.token, r.usuario); toast(`Hola, ${r.usuario.nombre.split(' ')[0]}`); destino(); };
forms.registro = async f => {
  const d = Object.fromEntries(new FormData(f)); await api.registrar(d);
  const r = await api.login({ email: d.email, password: d.password }); session.set(r.token, r.usuario); toast('Cuenta creada'); destino();
};

const TABS = [['perfil', 'Perfil'], ['socio', 'Socio'], ['entradas', 'Mis entradas'], ['pedidos', 'Mis pedidos'], ['historial', 'Actividad'], ['avisos', 'Avisos']];
async function cuenta(tab) {
  const u = session.user, cuerpo = { perfil, socio, entradas, pedidos, historial, avisos }[tab] || perfil;
  return { html: html`<div class="cab"><h1>Mi cuenta</h1><span class="suave">${u.email} · <span class="etq">${u.rol}</span></span></div>
    <nav class="tabs" aria-label="Secciones de la cuenta">${TABS.map(([k, t]) => html`<a href="#/cuenta/${k}" ${k === tab ? 'aria-current="true"' : ''}>${t}</a>`)}</nav><section>${await cuerpo(u)}</section>` };
}
async function perfil(u) {
  const d = await api.usuario(u.usuarioId);
  return html`<form data-form="perfil" style="max-width:480px"><div class="campo"><label for="n">Nombre</label><input id="n" name="nombre" value="${d.nombre}" required></div><div class="campo"><label for="t">Teléfono</label><input id="t" name="telefono" value="${d.telefono ?? ''}" autocomplete="tel"></div>
    <div class="campo"><label for="d">Dirección</label><input id="d" name="direccion" value="${d.direccion ?? ''}" autocomplete="street-address"></div><div class="err"></div><button class="btn" type="submit">Guardar cambios</button></form>`;
}
forms.perfil = async f => { const d = await api.editarUsuario(session.user.usuarioId, Object.fromEntries(new FormData(f))); session.set(session.token, { ...session.user, ...d }); toast('Cambios guardados'); };
async function socio(u) {
  const s = await api.socio(u.usuarioId);
  return s.estado === 'no_socio' ? html`<div class="aviso">Todavía no eres socio. El alta de socios la gestiona la secretaría del club: con la cuota activa tendrás tarifa de socio en las entradas y descuento en la tienda.</div>`
    : html`<dl class="resumen" style="max-width:420px;position:static"><div><dt>Estado</dt><dd><span class="etq ${s.estado}">${s.estado}</span></dd></div><div><dt>Nº de socio</dt><dd>${s.numeroSocio ?? '–'}</dd></div><div><dt>Cuota</dt><dd>${s.tipoCuota ?? '–'}</dd></div><div><dt>Caduca</dt><dd>${s.fechaCaducidad ?? '–'}</dd></div><div><dt>Descuento</dt><dd>${s.descuentoPorcentaje} %</dd></div></dl>`;
}
async function entradas(u) {
  const es = await api.misEntradas(u.usuarioId);
  return es.length ? html`${es.sort((a, b) => b.fechaCompra.localeCompare(a.fechaCompra)).map(e => html`${ticket(e)}${e.estado === 'valida' && session.staff ? '' : ''}`)}` : vacio(html`Aún no has comprado entradas. <a href="#/partidos">Ver partidos</a>`);
}
async function pedidos(u) {
  const ps = await api.pedidos();
  return ps.length ? html`<div class="tabla-w"><table><thead><tr><th>Pedido</th><th>Fecha</th><th class="izq">Artículos</th><th>Total</th><th>Estado</th><th></th></tr></thead><tbody>${ps.map(p => html`<tr><td>${p.pedidoId.slice(0, 8)}</td><td>${fmt.fechaHora(p.fechaPedido)}</td><td class="izq">${p.items.map(i => `${i.cantidad}× ${i.nombre}${i.talla ? ' (' + i.talla + ')' : ''}`).join(', ')}</td><td class="fuerte">${fmt.euros(p.total)}</td><td><span class="etq ${p.estado}">${p.estado}</span></td>
    <td>${['pagado', 'preparando'].includes(p.estado) ? html`<button class="btn peligro chico" data-act="cancelar-pedido" data-id="${p.pedidoId}">Cancelar</button>` : ''}</td></tr>`)}</tbody></table></div>` : vacio(html`Aún no has hecho pedidos. <a href="#/tienda">Ir a la tienda</a>`);
}
acts['cancelar-pedido'] = async el => { await api.cancelarPedido(el.dataset.id); toast('Pedido cancelado'); window.dispatchEvent(new HashChangeEvent('hashchange')); };
async function historial(u) {
  const h = await api.historial(u.usuarioId);
  return h.length ? html`<ul class="lista">${h.map(a => html`<li style="grid-template-columns:130px 1fr auto"><span class="suave peq">${fmt.fechaHora(a.fecha)}</span><span><span class="etq">${a.tipo}</span> ${a.descripcion ?? a.referenciaId}</span><span class="num">${a.importe != null ? fmt.euros(a.importe) : ''}</span></li>`)}</ul>` : vacio('Cuando compres entradas o artículos aparecerán aquí. Puede tardar unos segundos en reflejarse.');
}
async function avisos(u) {
  const [pr, jm] = await Promise.all([api.prefs(u.usuarioId).catch(e => e.status === 404 ? { jugadoresFavoritos: [], categoriasFavoritas: [], avisarInicioPartido: true } : Promise.reject(e)), jugadoresMapa()]);
  return html`<form data-form="avisos" style="max-width:560px"><fieldset style="border:0;padding:0;margin:0 0 18px"><legend style="font-weight:700;margin-bottom:8px">Jugadores favoritos</legend><div class="fila">${[...jm.values()].map(j => html`<label class="chk"><input type="checkbox" name="jugadoresFavoritos" value="${j.jugadorId}" ${pr.jugadoresFavoritos?.includes(j.jugadorId) ? 'checked' : ''}> ${j.nombre}</label>`)}</div></fieldset>
    <fieldset style="border:0;padding:0;margin:0 0 18px"><legend style="font-weight:700;margin-bottom:8px">Categorías de noticias</legend><div class="fila">${Object.entries(CAT).map(([k, t]) => html`<label class="chk"><input type="checkbox" name="categoriasFavoritas" value="${k}" ${pr.categoriasFavoritas?.includes(k) ? 'checked' : ''}> ${t}</label>`)}</div></fieldset>
    <label class="chk" style="margin-bottom:18px"><input type="checkbox" name="avisarInicioPartido" ${pr.avisarInicioPartido ? 'checked' : ''}> Avisarme cuando empiece un partido</label><div class="err"></div><button class="btn" type="submit">Guardar preferencias</button></form>`;
}
forms.avisos = async f => {
  const d = new FormData(f); await api.guardarPrefs(session.user.usuarioId, { jugadoresFavoritos: d.getAll('jugadoresFavoritos'), categoriasFavoritas: d.getAll('categoriasFavoritas'), avisarInicioPartido: d.has('avisarInicioPartido') }); toast('Preferencias guardadas');
};
