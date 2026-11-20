// Panel de gestión para staff/admin. Los recursos CRUD se describen con datos (RES); el resto son vistas propias.
import { api, session } from '../api.js';
import { html, fmt, vacio, aviso, modal, cerrarModal, toast, acts, forms, ETQ, POS, CAT } from '../ui.js';
import { jugadoresMapa, limpiarCacheJugadores } from '../comun.js';

const opts = o => Object.entries(o).map(([v, t]) => [v, t]);
const loc = iso => { const d = new Date(iso); return new Date(d - d.getTimezoneOffset() * 6e4).toISOString().slice(0, 16); };
const STOCK = { aForm: s => (s || []).map(t => `${t.talla}:${t.unidades}`).join(', '), deForm: v => v.split(',').map(x => x.trim()).filter(Boolean).map(x => { const [talla, u] = x.split(':'); return { talla: talla.trim(), unidades: parseInt(u, 10) || 0 }; }) };
const RES = {
  partidos: { t: 'Partidos', svc: 'deportivo', path: '/partidos', id: 'partidoId', del: 'admin', cols: [['fechaHora', 'Fecha', fmt.fechaHora], ['rival', 'Rival'], ['estado', 'Estado', v => ETQ[v]], ['jornada', 'J']],
    f: [['fechaHora', 'Fecha y hora', 'datetime'], ['rival', 'Rival'], ['lugar', 'Lugar'], ['esLocal', 'Juega en casa', 'check'], ['temporada', 'Temporada (AAAA-AAAA)'], ['competicion', 'Competición', 'text', 1], ['jornada', 'Jornada', 'number', 1], ['estado', 'Estado', 'select', 1, opts(ETQ)], ['puntosLocal', 'Puntos del equipo local', 'number', 1], ['puntosVisitante', 'Puntos del visitante', 'number', 1], ['ventaEntradasAbierta', 'Venta de entradas abierta', 'check']] },
  jugadores: { t: 'Jugadores', svc: 'deportivo', path: '/jugadores', id: 'jugadorId', del: 'admin', todos: true, cols: [['dorsal', '#'], ['nombre', 'Nombre'], ['posicion', 'Posición', v => POS[v]], ['activo', 'Activo', v => v ? 'Sí' : 'Baja']],
    f: [['nombre', 'Nombre'], ['dorsal', 'Dorsal', 'number'], ['posicion', 'Posición', 'select', 0, opts(POS)], ['alturaCm', 'Altura (cm)', 'number', 1], ['fechaNacimiento', 'Fecha de nacimiento', 'date', 1], ['activo', 'En activo', 'check']] },
  noticias: { t: 'Noticias', svc: 'deportivo', path: '/noticias', id: 'noticiaId', del: 'staff', cols: [['fechaPublicacion', 'Fecha', fmt.dia], ['titulo', 'Título'], ['categoria', 'Categoría', v => CAT[v]]],
    f: [['titulo', 'Título'], ['categoria', 'Categoría', 'select', 0, opts(CAT)], ['contenido', 'Contenido', 'area'], ['imagenUrl', 'URL de imagen', 'text', 1]] },
  productos: { t: 'Productos', svc: 'compras', path: '/productos', id: 'productoId', del: 'admin', cols: [['nombre', 'Producto'], ['categoria', 'Categoría'], ['precio', 'Precio', fmt.euros], ['stock', 'Stock', s => (s || []).map(t => `${t.talla}:${t.unidades}`).join(' ')]],
    f: [['nombre', 'Nombre'], ['categoria', 'Categoría', 'select', 0, opts({ camiseta: 'Camiseta', bufanda: 'Bufanda', gorra: 'Gorra', accesorio: 'Accesorio', otro: 'Otro' })], ['precio', 'Precio (€)', 'number'], ['descripcion', 'Descripción', 'area', 1], ['stock', 'Stock por talla', 'stock', 1]] },
  patrocinadores: { t: 'Patrocinadores', svc: 'institucional', path: '/patrocinadores', id: 'patrocinadorId', del: 'admin', admin: true, cols: [['nombre', 'Nombre'], ['nivel', 'Nivel'], ['enlace', 'Web']],
    f: [['nombre', 'Nombre'], ['nivel', 'Nivel', 'select', 0, opts({ principal: 'Principal', oro: 'Oro', plata: 'Plata', bronce: 'Bronce', colaborador: 'Colaborador' })], ['enlace', 'Web (https://…)', 'url', 1], ['logoUrl', 'URL del logo (https://… o /img/…)', 'text', 1]] },
  palmares: { t: 'Palmarés', svc: 'institucional', path: '/club/palmares', id: 'tituloId', del: 'admin', cols: [['temporada', 'Temporada'], ['competicion', 'Competición'], ['descripcion', 'Detalle']],
    f: [['competicion', 'Competición'], ['temporada', 'Temporada (AAAA-AAAA)'], ['descripcion', 'Detalle', 'text', 1]] },
};
const SECC = [...Object.entries(RES).map(([k, r]) => [k, r.t]), ['pedidos', 'Pedidos'], ['directo', 'Partido en directo'], ['acceso', 'Control de acceso'], ['usuarios', 'Usuarios']];
const visible = ([k]) => k === 'usuarios' || RES[k]?.admin ? session.admin : true;
let CUR = {};

export default async function ({ sec = 'partidos' }) {
  const body = RES[sec] ? await crud(sec) : { pedidos, directo, acceso, usuarios }[sec] ? await ({ pedidos, directo, acceso, usuarios })[sec]() : aviso('Sección desconocida', 'error');
  return { html: html`<div class="cab"><h1>Gestión</h1><span class="suave">${session.user.rol}</span></div>
    <nav class="tabs" aria-label="Secciones">${SECC.filter(visible).map(([k, t]) => html`<a href="#/gestion/${k}" ${k === sec ? 'aria-current="true"' : ''}>${t}</a>`)}</nav><section>${body}</section>` };
}
const recargar = () => window.dispatchEvent(new HashChangeEvent('hashchange'));

// ── CRUD genérico ──
async function crud(sec) {
  const r = RES[sec], c0 = api.crud(r.svc, r.path), rows = r.todos ? (await Promise.all([c0.list({ activo: true }), c0.list({ activo: false })])).flat() : await c0.list(r.q); CUR[sec] = new Map(rows.map(x => [x[r.id], x]));
  const puedeBorrar = r.del === 'staff' || session.admin;
  return html`<div class="cab"><span class="suave">${rows.length} registros</span><button class="btn" data-act="nuevo" data-sec="${sec}">Añadir</button></div>
    ${rows.length ? html`<div class="tabla-w"><table><thead><tr>${r.cols.map(c => html`<th class="izq">${c[1]}</th>`)}<th></th></tr></thead><tbody>${rows.map(x => html`<tr>${r.cols.map(([k, , f]) => html`<td class="izq">${f ? f(x[k]) : x[k] ?? '–'}</td>`)}<td class="der"><button class="btn sec chico" data-act="editar" data-sec="${sec}" data-id="${x[r.id]}">Editar</button> ${puedeBorrar ? html`<button class="btn peligro chico" data-act="borrar" data-sec="${sec}" data-id="${x[r.id]}">Eliminar</button>` : ''}</td></tr>`)}</tbody></table></div>` : vacio('No hay registros.')}`;
}
function formulario(sec, x) {
  const r = RES[sec];
  const campo = ([n, l, tipo = 'text', opt, o]) => { const v = x?.[n], req = opt ? '' : 'required', id = 'f-' + n;
    if (tipo === 'check') return html`<label class="chk campo"><input type="checkbox" name="${n}" ${(x ? v : n === 'activo' || n === 'esLocal') ? 'checked' : ''}> ${l}</label>`;
    return html`<div class="campo"><label for="${id}">${l}</label>${tipo === 'select' ? html`<select id="${id}" name="${n}" ${req}>${o.map(([val, t]) => html`<option value="${val}" ${v === val ? 'selected' : ''}>${t}</option>`)}</select>`
      : tipo === 'area' ? html`<textarea id="${id}" name="${n}" ${req}>${v ?? ''}</textarea>`
      : html`<input id="${id}" name="${n}" ${req} type="${{ datetime: 'datetime-local', number: 'number', date: 'date', stock: 'text', url: 'url' }[tipo] || 'text'}" ${tipo === 'number' ? 'step="any"' : ''} value="${v == null ? '' : tipo === 'datetime' ? loc(v) : tipo === 'stock' ? STOCK.aForm(v) : v}" ${tipo === 'stock' ? 'placeholder="S:10, M:5, L:0"' : ''}>`}</div>`; };
  modal(html`<h2>${x ? 'Editar' : 'Añadir'} · ${r.t.toLowerCase()}</h2><form data-form="crud" data-sec="${sec}" data-id="${x?.[r.id] ?? ''}">${r.f.map(campo)}<div class="err"></div><button class="btn" type="submit">Guardar</button></form>`);
}
acts.nuevo = el => formulario(el.dataset.sec);
acts.editar = el => formulario(el.dataset.sec, CUR[el.dataset.sec].get(el.dataset.id));
acts.borrar = async el => { if (!confirm('¿Eliminar este registro? No se puede deshacer.')) return; const r = RES[el.dataset.sec]; await api.crud(r.svc, r.path).borrar(el.dataset.id); if (el.dataset.sec === 'jugadores') limpiarCacheJugadores(); toast('Eliminado'); recargar(); };
forms.crud = async f => {
  const sec = f.dataset.sec, r = RES[sec], fd = new FormData(f), out = {};
  for (const [n, , tipo = 'text'] of r.f) {
    const v = fd.get(n);
    if (tipo === 'check') out[n] = fd.has(n); else if (v === '' || v == null) continue;
    else out[n] = tipo === 'number' ? Number(v) : tipo === 'datetime' ? new Date(v).toISOString() : tipo === 'stock' ? STOCK.deForm(v) : v;
  }
  const c = api.crud(r.svc, r.path), prev = f.dataset.id ? CUR[sec].get(f.dataset.id) : null;
  await (prev ? c.editar(f.dataset.id, { ...prev, ...out }) : c.crear(out)); if (sec === 'jugadores') limpiarCacheJugadores(); cerrarModal(); toast('Guardado'); recargar();
};

// ── Pedidos ──
const SIG = { pagado: 'preparando', preparando: 'enviado', enviado: 'entregado' };
async function pedidos() {
  const ps = await api.pedidos();
  return ps.length ? html`<div class="tabla-w"><table><thead><tr><th>Pedido</th><th>Fecha</th><th class="izq">Artículos</th><th>Total</th><th>Estado</th><th></th></tr></thead><tbody>${ps.map(p => html`<tr><td>${p.pedidoId.slice(0, 8)}</td><td>${fmt.fechaHora(p.fechaPedido)}</td><td class="izq">${p.items.map(i => `${i.cantidad}× ${i.nombre}${i.talla ? ' (' + i.talla + ')' : ''}`).join(', ')}<div class="peq suave">${p.direccionEnvio}</div></td><td>${fmt.euros(p.total)}</td><td><span class="etq ${p.estado}">${p.estado}</span></td>
    <td class="der">${SIG[p.estado] ? html`<button class="btn chico" data-act="avanzar" data-id="${p.pedidoId}" data-est="${SIG[p.estado]}">Marcar ${SIG[p.estado]}</button>` : ''} ${!['enviado', 'entregado', 'cancelado'].includes(p.estado) ? html`<button class="btn peligro chico" data-act="cancelar-pedido" data-id="${p.pedidoId}">Cancelar</button>` : ''}</td></tr>`)}</tbody></table></div>` : vacio('No hay pedidos.');
}
acts.avanzar = async el => { await api.estadoPedido(el.dataset.id, el.dataset.est); recargar(); };

// ── Estadísticas en directo (alta de eventos) ──
const EV = [['tiro_2_anotado', '+2'], ['tiro_3_anotado', '+3'], ['tiro_libre_anotado', '+1 TL'], ['tiro_2_fallado', 'Falla 2'], ['tiro_3_fallado', 'Falla 3'], ['tiro_libre_fallado', 'Falla TL'], ['rebote_ofensivo', 'Reb. of.'], ['rebote_defensivo', 'Reb. def.'], ['asistencia', 'Asistencia'], ['robo', 'Robo'], ['perdida', 'Pérdida'], ['tapon', 'Tapón'], ['falta_personal', 'Falta'], ['falta_recibida', 'F. recibida']];
const GEN = [['inicio_partido', 'Inicio partido'], ['inicio_periodo', 'Inicio periodo'], ['fin_periodo', 'Fin periodo'], ['fin_partido', 'Fin partido']];
async function directo() {
  const [ps, jm] = await Promise.all([api.partidos(), jugadoresMapa()]);
  return html`<div class="fila" style="margin-bottom:14px"><div class="campo"><label for="dp">Partido</label><select id="dp" data-cambia="dp">${ps.map(p => html`<option value="${p.partidoId}" ${p.estado === 'en_juego' ? 'selected' : ''}>J${p.jornada} · ${p.rival} (${ETQ[p.estado]})</option>`)}</select></div>
    <div class="campo"><label for="dj">Jugador</label><select id="dj">${[...jm.values()].map(j => html`<option value="${j.jugadorId}">${j.dorsal} · ${j.nombre}</option>`)}</select></div>
    <div class="campo"><label for="dper">Periodo</label><input id="dper" type="number" min="1" max="10" value="1"></div><div class="campo"><label for="dseg">Segundos restantes</label><input id="dseg" type="number" min="0" max="600" value="600"></div></div>
    <h3 style="margin-bottom:8px">Acciones del jugador</h3><div class="evto">${EV.map(([t, l]) => html`<button class="btn sec" data-act="evento" data-tipo="${t}">${l}</button>`)}</div>
    <h3 style="margin:18px 0 8px">Acciones generales</h3><div class="evto">${GEN.map(([t, l]) => html`<button class="btn sec" data-act="evento" data-tipo="${t}" data-gen="1">${l}</button>`)}</div>
    <h3 style="margin:18px 0 8px">Últimos eventos</h3><div id="evlista">${await listaEventos(ps.find(p => p.estado === 'en_juego')?.partidoId || ps[0]?.partidoId, jm)}</div>
    <p class="peq suave" style="margin-top:10px">Los eventos se envían con identificador único; si repites uno, el servicio lo rechaza con 409. Los asistentes al partido lo ven al instante por WebSocket.</p>`;
}
async function listaEventos(pid, jm) {
  if (!pid) return vacio('Sin partidos.'); const ev = (await api.eventos(pid).catch(() => [])).filter(e => !e.anulado).slice(-8).reverse();
  return ev.length ? html`<ul class="lista">${ev.map(e => html`<li style="grid-template-columns:1fr auto"><span><b>${e.tipo.replaceAll('_', ' ')}</b> ${e.jugadorId ? '· ' + (jm.get(e.jugadorId)?.nombre ?? '') : ''} <span class="suave peq">P${e.periodo}</span></span><button class="btn peligro chico" data-act="anular" data-id="${e.eventoId}">Anular</button></li>`)}</ul>` : vacio('Aún no hay eventos en este partido.');
}
const pid = () => document.getElementById('dp').value;
const refrescarEv = async () => { document.getElementById('evlista').innerHTML = (await listaEventos(pid(), await jugadoresMapa())).s; };
acts.evento = async el => {
  await api.registrarEvento(pid(), { clientEventoId: crypto.randomUUID(), tipo: el.dataset.tipo, ...(el.dataset.gen ? {} : { jugadorId: document.getElementById('dj').value }), periodo: +document.getElementById('dper').value, segundosRestantes: +document.getElementById('dseg').value });
  toast('Evento registrado'); await refrescarEv();
};
acts.anular = async el => { await api.anularEvento(pid(), el.dataset.id); toast('Evento anulado'); await refrescarEv(); };
document.addEventListener('change', e => { if (e.target.dataset?.cambia === 'dp') refrescarEv(); });

// ── Control de acceso ──
function acceso() {
  return html`<form data-form="validar" style="max-width:480px"><div class="campo"><label for="ent">Identificador de la entrada</label><input id="ent" name="id" required autocomplete="off"><div class="ayuda">Pega el identificador completo de la entrada.</div></div><div class="err"></div><button class="btn" type="submit">Validar entrada</button></form><div id="resval" style="margin-top:20px"></div>`;
}
forms.validar = async f => {
  const e = await api.validar(new FormData(f).get('id').trim());
  document.getElementById('resval').innerHTML = aviso(html`<b>Entrada válida.</b> Zona ${e.asiento.zona}, fila ${e.asiento.fila}, asiento ${e.asiento.numero}. Marcada como usada.`, 'ok').s;
};

// ── Usuarios (admin) ──
async function usuarios() {
  if (!session.admin) return aviso('Solo el administrador gestiona usuarios.', 'warn');
  const us = await api.usuarios(); CUR.us = new Map(us.map(u => [u.usuarioId, u]));
  return html`<div class="tabla-w"><table><thead><tr><th>Nombre</th><th class="izq">Email</th><th>Rol</th><th></th></tr></thead><tbody>${us.map(u => html`<tr><td class="izq"><b>${u.nombre}</b></td><td class="izq">${u.email}</td>
    <td><label class="vh" for="r${u.usuarioId}">Rol</label><select id="r${u.usuarioId}" data-rol="${u.usuarioId}" style="width:auto">${['visitante', 'socio', 'staff', 'admin'].map(r => html`<option ${u.rol === r ? 'selected' : ''}>${r}</option>`)}</select></td>
    <td class="der"><button class="btn sec chico" data-act="alta-socio" data-id="${u.usuarioId}">Alta de socio</button></td></tr>`)}</tbody></table></div>`;
}
document.addEventListener('change', async e => { const id = e.target.dataset?.rol; if (!id) return; try { await api.cambiarRol(id, e.target.value); toast('Rol actualizado'); } catch (err) { toast(err.message, 'error'); recargar(); } });
acts['alta-socio'] = el => { const u = CUR.us.get(el.dataset.id), hoy = new Date(Date.now() + 365 * 864e5).toISOString().slice(0, 10);
  modal(html`<h2>Alta de socio</h2><p class="suave">${u.nombre}</p><form data-form="socio" data-id="${u.usuarioId}"><div class="campo"><label for="tc">Cuota</label><select id="tc" name="tipoCuota">${['mensual', 'anual', 'juvenil', 'jubilado'].map(c => html`<option>${c}</option>`)}</select></div><div class="campo"><label for="fc">Caduca el</label><input id="fc" name="fechaCaducidad" type="date" value="${hoy}" required></div><div class="err"></div><button class="btn" type="submit">Guardar alta</button></form>`); };
forms.socio = async f => { await api.altaSocio(f.dataset.id, Object.fromEntries(new FormData(f))); cerrarModal(); toast('Socio dado de alta'); recargar(); };
