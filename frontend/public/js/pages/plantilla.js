import { api, TEMPORADA_DEFECTO } from '../api.js';
import { html, fmt, vacio, urlSegura, POS } from '../ui.js';
import { temporadaDe, resumenNoticia } from '../comun.js';

export default async function ({ id }) { return id ? ficha(id) : lista(); }

async function lista() {
  const js = (await api.jugadores()).sort((a, b) => a.dorsal - b.dorsal);
  const grupos = Object.keys(POS).map(k => [k, js.filter(j => j.posicion === k)]).filter(([, l]) => l.length);
  return { html: html`<div class="cab"><h1>Plantilla</h1><span class="suave">${js.length} jugadores en activo</span></div>
    ${js.length ? grupos.map(([k, l]) => html`<section><h2 style="margin-bottom:12px">${POS[k]}s</h2><div class="plantilla">${l.map(j => {
      const foto = urlSegura(j.fotoUrl);
      return html`<a class="jug" href="#/jugadores/${j.jugadorId}">${foto
        ? html`<img class="jug-foto" src="${foto}" alt="${j.nombre}" onerror="this.nextElementSibling.style.display='';this.remove()"><span class="dorsal" aria-hidden="true" style="display:none">${j.dorsal}</span>`
        : html`<span class="dorsal" aria-hidden="true">${j.dorsal}</span>`}<strong>${j.nombre}</strong><span class="suave peq">Dorsal ${j.dorsal}${j.alturaCm ? ` · ${j.alturaCm} cm` : ''}</span></a>`;
    })}</div></section>`) : vacio('La plantilla aún no está publicada.')}` };
}

async function ficha(id) {
  const j = await api.jugador(id), ps = await api.partidos().catch(() => []), t = temporadaDe(ps);
  const [s, hist, noticias] = await Promise.all([api.statsJugador(id, t).catch(() => null), api.historicoJugador(id, t).catch(() => []), api.noticias({ jugadorId: id }).catch(() => [])]);
  const pr = s?.promedios, edad = j.fechaNacimiento ? Math.floor((Date.now() - new Date(j.fechaNacimiento)) / 31557600000) : null, foto = urlSegura(j.fotoUrl);
  return { html: html`<p><a href="#/plantilla">← Plantilla</a></p>
    <section class="ficha">${foto ? html`<img class="ficha-foto" src="${foto}" alt="${j.nombre}" onerror="this.remove()">` : ''}<div class="dorsal-g" aria-hidden="true">${j.dorsal}</div><div><h1>${j.nombre}</h1><p class="suave" style="margin-top:10px">${POS[j.posicion]}${j.alturaCm ? ` · ${j.alturaCm} cm` : ''}${edad ? ` · ${edad} años` : ''}${j.activo ? '' : ' · Baja'}</p></div></section>
    <section><h2 style="margin-bottom:12px">Temporada ${t}</h2>
      ${s ? html`<div class="tabla-w"><table><thead><tr>${['PJ', 'Min', 'Pts', 'Reb', 'Ast', 'Rob', 'Tap', 'T2 %', 'T3 %', 'TL %'].map(h => html`<th>${h}</th>`)}</tr></thead><tbody><tr>
        ${[s.partidosJugados, pr.minutosPorPartido, pr.puntosPorPartido, pr.rebotesPorPartido, pr.asistenciasPorPartido, pr.robosPorPartido, pr.taponesPorPartido, s.porcentajes.tiros2, s.porcentajes.tiros3, s.porcentajes.tirosLibres].map(v => html`<td class="fuerte">${fmt.num(v)}</td>`)}</tr></tbody></table></div><p class="peq suave" style="margin-top:6px">Promedios por partido.</p>`
        : vacio('Este jugador todavía no tiene estadísticas esta temporada.')}</section>
    ${hist.length ? html`<section><h2 style="margin-bottom:12px">Partido a partido</h2><div class="tabla-w"><table><thead><tr><th>Jornada</th><th>Min</th><th>Pts</th><th>Reb</th><th>Ast</th><th>Val</th></tr></thead><tbody>
      ${hist.map(h => html`<tr><td><a href="#/partidos/${h.partidoId}">J${h.jornada ?? '–'}</a></td><td>${fmt.min(h.segundosJugados)}</td><td class="fuerte">${h.puntos}</td><td>${h.rebotesTotales}</td><td>${h.asistencias}</td><td>${fmt.num(h.valoracion)}</td></tr>`)}</tbody></table></div></section>` : ''}
    ${noticias.length ? html`<section><h2 style="margin-bottom:8px">En las noticias</h2>${noticias.map(n => resumenNoticia(n))}</section>` : ''}` };
}
