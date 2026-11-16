import { api } from '../api.js';
import { html, fmt, vacio, acts } from '../ui.js';
import { temporadaDe, jugadoresMapa } from '../comun.js';

const METRICAS = [['puntos_por_partido', 'Puntos por partido'], ['puntos', 'Puntos totales'], ['rebotes_por_partido', 'Rebotes por partido'], ['asistencias_por_partido', 'Asistencias por partido'], ['robos_por_partido', 'Robos por partido'],
  ['tapones_por_partido', 'Tapones por partido'], ['valoracion_por_partido', 'Valoración por partido'], ['porcentaje_tiros_3', '% triples'], ['porcentaje_tiros_libres', '% tiros libres'], ['true_shooting', 'True shooting %'], ['mas_menos', 'Más/menos']];

export default async function (_p, qs) {
  const metrica = qs.get('metrica') || 'puntos_por_partido', t = temporadaDe(await api.partidos());
  const [rank, tabla, jm] = await Promise.all([api.lideres(t, metrica, 10).catch(() => null), api.statsTemporada(t).catch(() => []), jugadoresMapa()]);
  const max = Math.max(...(rank?.jugadores.map(j => j.valor) || [1]), 1), nom = id => jm.get(id)?.nombre ?? 'Jugador';
  const filas = [...tabla].sort((a, b) => b.promedios.puntosPorPartido - a.promedios.puntosPorPartido);
  return { html: html`<div class="cab"><h1>Estadísticas</h1><span class="suave">Temporada ${t}</span></div>
    <section><div class="cab"><h2>Líderes</h2><div><label class="vh" for="met">Estadística</label><select id="met" onchange="location.hash='#/estadisticas?metrica='+this.value">${METRICAS.map(([k, n]) => html`<option value="${k}" ${k === metrica ? 'selected' : ''}>${n}</option>`)}</select></div></div>
      ${rank?.jugadores.length ? html`<div style="border-top:1px solid var(--tinta)">${rank.jugadores.map(l => html`<div class="barra"><span class="pos">${l.posicion}</span><div><a href="#/jugadores/${l.jugadorId}" style="font-weight:700;text-decoration:none">${nom(l.jugadorId)}</a><i style="width:${Math.max(3, Math.abs(l.valor) / max * 100)}%;margin-top:4px"></i></div><span class="num" style="font-size:1.5rem;text-align:right">${fmt.num(l.valor)}</span></div>`)}</div>` : vacio('Todavía no hay datos suficientes para este ranking.')}</section>
    <section><h2 style="margin-bottom:12px">Tabla de la temporada</h2>${filas.length ? html`<div class="tabla-w"><table><thead><tr><th>Jugador</th><th>PJ</th><th>Min</th><th>Pts</th><th>Reb</th><th>Ast</th><th>Rob</th><th>Tap</th><th>T2 %</th><th>T3 %</th><th>TL %</th><th>Val</th></tr></thead><tbody>
      ${filas.map(f => html`<tr><td><a href="#/jugadores/${f.jugadorId}" style="text-decoration:none;color:inherit">${nom(f.jugadorId)}</a></td><td>${f.partidosJugados}</td><td>${fmt.num(f.promedios.minutosPorPartido)}</td><td class="fuerte">${fmt.num(f.promedios.puntosPorPartido)}</td><td>${fmt.num(f.promedios.rebotesPorPartido)}</td><td>${fmt.num(f.promedios.asistenciasPorPartido)}</td><td>${fmt.num(f.promedios.robosPorPartido)}</td><td>${fmt.num(f.promedios.taponesPorPartido)}</td><td>${fmt.num(f.porcentajes.tiros2)}</td><td>${fmt.num(f.porcentajes.tiros3)}</td><td>${fmt.num(f.porcentajes.tirosLibres)}</td><td>${fmt.num(f.promedios.valoracionPorPartido)}</td></tr>`)}</tbody></table></div><p class="peq suave" style="margin-top:6px">Promedios por partido.</p>` : vacio('La temporada aún no tiene estadísticas.')}</section>` };
}
