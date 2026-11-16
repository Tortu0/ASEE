import { api, cfg } from '../api.js';
import { html, raw, fmt, vacio, aviso, onLeave, acts } from '../ui.js';
import { filaPartido, etiqueta, marcadorNuestro, jugadoresMapa } from '../comun.js';

export default async function (params) { return params.id ? detalle(params.id) : listado(); }

async function listado() {
  const ps = (await api.partidos()).sort((a, b) => a.fechaHora.localeCompare(b.fechaHora));
  const futuros = ps.filter(p => ['programado', 'aplazado', 'en_juego', 'suspendido'].includes(p.estado)), pasados = ps.filter(p => p.estado === 'finalizado').reverse();
  return { html: html`<div class="cab"><h1>Partidos</h1></div>
    <section><h2 style="margin-bottom:12px">Próximos</h2>${futuros.length ? html`<ul class="lista">${futuros.map(filaPartido)}</ul>` : vacio('No hay partidos programados.')}</section>
    <section><h2 style="margin-bottom:12px">Resultados</h2>${pasados.length ? html`<ul class="lista">${pasados.map(filaPartido)}</ul>` : vacio('Aún no se ha jugado ningún partido.')}</section>` };
}

const COLS = [['Min', 'seg', v => fmt.min(v)], ['Pts', 'puntos'], ['T2', null, j => `${j.tiros2Anotados}/${j.tiros2Intentados}`], ['T3', null, j => `${j.tiros3Anotados}/${j.tiros3Intentados}`],
  ['TL', null, j => `${j.tirosLibresAnotados}/${j.tirosLibresIntentados}`], ['Reb', 'rebotesTotales'], ['Ast', 'asistencias'], ['Rob', 'robos'], ['Tap', 'tapones'], ['Per', 'perdidas'], ['Fal', 'faltasCometidas'], ['+/−', 'masMenos'], ['Val', 'valoracion']];
const celda = (j, [, k, f]) => k === 'seg' ? fmt.min(j.segundosJugados) : f ? f(j) : fmt.num(j[k], 1);

function boxScore(stats, jm) {
  const js = [...stats.jugadores].sort((a, b) => (b.titular - a.titular) || b.puntos - a.puntos);
  if (!js.length) return vacio('Todavía no hay estadísticas registradas para este partido.');
  const suma = k => js.reduce((s, j) => s + (j[k] || 0), 0), tot = { puntos: suma('puntos'), rebotesTotales: suma('rebotesTotales'), asistencias: suma('asistencias'), robos: suma('robos'), tapones: suma('tapones'), perdidas: suma('perdidas'), faltasCometidas: suma('faltasCometidas'),
    tiros2Anotados: suma('tiros2Anotados'), tiros2Intentados: suma('tiros2Intentados'), tiros3Anotados: suma('tiros3Anotados'), tiros3Intentados: suma('tiros3Intentados'), tirosLibresAnotados: suma('tirosLibresAnotados'), tirosLibresIntentados: suma('tirosLibresIntentados'), segundosJugados: suma('segundosJugados'), masMenos: null, valoracion: suma('valoracion') };
  return html`<div class="tabla-w"><table><thead><tr><th>Jugador</th>${COLS.map(c => html`<th scope="col">${c[0]}</th>`)}</tr></thead><tbody>
    ${js.map(j => { const p = jm.get(j.jugadorId); return html`<tr><td class="izq"><a href="#/jugadores/${j.jugadorId}" style="text-decoration:none;color:inherit"><span class="num suave" style="margin-right:8px">${p?.dorsal ?? ''}</span>${p?.nombre ?? 'Jugador'}</a>${j.titular ? html` <span class="suave peq">★</span>` : ''}</td>${COLS.map(c => html`<td class="${c[0] === 'Pts' ? 'fuerte' : ''}">${celda(j, c)}</td>`)}</tr>`; })}
    <tr style="font-weight:700"><td class="izq">Equipo</td>${COLS.map(c => html`<td>${c[0] === '+/−' ? '' : celda(tot, c)}</td>`)}</tr></tbody></table></div>
    <p class="peq suave" style="margin-top:8px">★ titular. Actualizado ${fmt.hora(stats.actualizadoEn)}.</p>`;
}

async function detalle(id) {
  const [p, jm] = await Promise.all([api.partido(id), jugadoresMapa()]);
  const m = marcadorNuestro(p), nombre = window.CLUB?.nombreOficial || 'Nosotros';
  const [aforo, stats0, eventos] = await Promise.all([p.ventaEntradasAbierta ? api.aforo(id).catch(() => null) : null, api.statsPartido(id).catch(e => ({ error: e })), api.eventos(id).catch(() => [])]);
  const vivo = p.estado === 'en_juego';
  return { html: html`<p><a href="#/partidos">← Todos los partidos</a></p>
    <section class="hero" style="background:var(--tinta)"><span class="estado">${vivo ? html`<span class="vivo"></span> En juego` : etiqueta(p.estado)} <span id="reloj" class="peq"></span></span>
      <h1 style="max-width:none;margin:12px 0">${nombre} <span style="opacity:.55">${p.esLocal ? 'vs' : '@'}</span> ${p.rival}</h1>
      ${m ? html`<div class="marcador"><span class="pts" id="ptsN">${m[0]}</span><span class="sep">–</span><span class="pts">${m[1]}</span></div>` : ''}
      <p style="margin-top:12px">${fmt.diaLargo(p.fechaHora)}, ${fmt.hora(p.fechaHora)}. ${p.lugar}. ${p.competicion ?? ''} · Jornada ${p.jornada ?? '–'}.</p>
      ${p.ventaEntradasAbierta && p.estado === 'programado' ? html`<div class="acciones"><a class="btn" href="#/entradas/${id}">Comprar entradas</a>${aforo ? html`<span class="peq" style="align-self:center">${aforo.libres} de ${aforo.capacidadTotal} asientos libres</span>` : ''}</div>` : ''}</section>
    <section><div class="cab"><h2>Estadísticas</h2><span class="peq suave" id="canal">${vivo ? 'Conectando…' : ''}</span></div>
      <div id="box">${stats0.error ? (stats0.error.status === 404 ? vacio('Todavía no hay estadísticas registradas para este partido.') : aviso(`No se pudieron cargar las estadísticas: ${stats0.error.message}`, 'warn')) : boxScore(stats0, jm)}</div></section>
    ${eventos.length ? html`<section><h2 style="margin-bottom:12px">Jugada a jugada</h2><ul class="lista" id="pbp">${pbp(eventos, jm)}</ul></section>` : ''}`,
    mount: () => { if (vivo) directo(id, jm); } };
}
const TXT = { tiro_2_anotado: 'anota un tiro de 2', tiro_2_fallado: 'falla un tiro de 2', tiro_3_anotado: 'anota un triple', tiro_3_fallado: 'falla un triple', tiro_libre_anotado: 'anota un tiro libre', tiro_libre_fallado: 'falla un tiro libre', rebote_ofensivo: 'rebote ofensivo', rebote_defensivo: 'rebote defensivo', asistencia: 'asistencia', robo: 'robo', perdida: 'pérdida', tapon: 'tapón', tapon_recibido: 'tapón recibido', falta_personal: 'falta personal', falta_recibida: 'falta recibida', inicio_partido: 'Comienza el partido', fin_partido: 'Fin del partido', inicio_periodo: 'Comienza el periodo', fin_periodo: 'Fin del periodo', entra_pista: 'entra en pista', sale_pista: 'sale de la pista' };
const pbp = (ev, jm) => [...ev].filter(e => !e.anulado).sort((a, b) => b.registradoEn.localeCompare(a.registradoEn)).slice(0, 15)
  .map(e => html`<li style="grid-template-columns:70px 1fr"><span class="num suave">P${e.periodo} ${e.segundosRestantes != null ? fmt.min(e.segundosRestantes) : ''}</span><span>${e.jugadorId ? html`<b>${jm.get(e.jugadorId)?.nombre ?? 'Jugador'}</b> ` : ''}${TXT[e.tipo] ?? e.tipo}</span></li>`);

// Canal WebSocket de solo lectura (memoria §3.5): suscripción → instantánea completa con versión creciente.
function directo(partidoId, jm) {
  let ws, ultima = -1, intento = 0, cerrado = false, poll;
  const box = () => document.getElementById('box'), canal = t => { const c = document.getElementById('canal'); if (c) c.innerHTML = t; };
  const pintar = d => { if (!box()) return; box().innerHTML = boxScore(d, jm).s; document.getElementById('reloj').textContent = d.periodoActual ? `Periodo ${d.periodoActual} · ${fmt.min(d.segundosRestantesPeriodo)}` : ''; };
  const conectar = () => {
    ws = new WebSocket(cfg.wsUrl);
    ws.onopen = () => { intento = 0; clearInterval(poll); canal('<span class="vivo"></span> En directo'); ws.send(JSON.stringify({ tipo: 'suscribir', partidoId })); };
    ws.onmessage = ({ data }) => { const m = JSON.parse(data);
      if (m.tipo === 'heartbeat') ws.send('{"tipo":"pong"}');
      else if (m.tipo === 'estado' && m.version > ultima) { ultima = m.version; pintar(m.datos); api.eventos(partidoId).then(ev => { const l = document.getElementById('pbp'); l && (l.innerHTML = pbp(ev, jm).map(String).join('')); }).catch(() => {}); }
      else if (m.tipo === 'error') canal(`Canal en directo: ${m.mensaje}`); };
    ws.onclose = () => { if (cerrado) return; canal('Reconectando… (se actualiza cada 10 s)'); clearInterval(poll); poll = setInterval(() => api.statsPartido(partidoId).then(pintar).catch(() => {}), 10000); setTimeout(conectar, Math.min(15000, 1000 * 2 ** intento++)); };
  };
  conectar(); onLeave(() => { cerrado = true; clearInterval(poll); ws?.close(); });
}
