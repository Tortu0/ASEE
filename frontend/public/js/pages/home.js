import { api } from '../api.js';
import { html, fmt, vacio } from '../ui.js';
import { PISTA, patro, filaPartido, resumenNoticia, marcadorNuestro, temporadaDe, jugadoresMapa } from '../comun.js';

export default async function () {
  const [partidos, noticias, patros] = await Promise.all([api.partidos(), api.noticias(), api.patrocinadores().catch(() => [])]);
  const orden = [...partidos].sort((a, b) => a.fechaHora.localeCompare(b.fechaHora));
  const vivo = orden.find(p => p.estado === 'en_juego'), prox = orden.filter(p => p.estado === 'programado' && new Date(p.fechaHora) > Date.now());
  const foco = vivo || prox[0], ult = [...orden].reverse().find(p => p.estado === 'finalizado'), nombre = window.CLUB?.nombreOficial || 'El club';
  const lideres = await api.lideres(temporadaDe(partidos), 'puntos_por_partido', 3).catch(() => null), jm = lideres ? await jugadoresMapa() : null;
  const m = foco && marcadorNuestro(foco);
  return { html: html`
  <section class="hero" aria-labelledby="h-hero">${PISTA}
    ${foco ? html`
      <span class="estado">${vivo ? html`<span class="vivo"></span> En juego ahora` : html`Próximo partido · ${fmt.cuenta(foco.fechaHora)}`}</span>
      <h1 id="h-hero">${nombre} ${foco.esLocal ? 'recibe al' : 'visita al'} ${foco.rival}</h1>
      ${vivo && m ? html`<div class="marcador"><span class="pts">${m[0]}</span><span class="sep">–</span><span class="pts">${m[1]}</span></div>`
        : html`<div class="marcador"><span class="pts">${fmt.hora(foco.fechaHora)}</span></div>`}
      <p>${fmt.diaLargo(foco.fechaHora)}. ${foco.lugar}.</p>
      <div class="acciones">
        ${foco.ventaEntradasAbierta && !vivo ? html`<a class="btn" href="#/entradas/${foco.partidoId}">Comprar entradas</a>` : ''}
        <a class="btn sec" href="#/partidos/${foco.partidoId}">${vivo ? 'Seguir el partido' : 'Ver partido'}</a></div>`
    : html`<h1 id="h-hero">${nombre}</h1><p>No hay partidos programados. Consulta los resultados de la temporada.</p><div class="acciones"><a class="btn sec" href="#/partidos">Ver calendario</a></div>`}
  </section>

  <div class="dos">
    <section><div class="cab"><h2>Calendario</h2><a href="#/partidos">Ver todo</a></div>
      <ul class="lista">${[ult, ...prox.slice(0, 3)].filter(Boolean).map(filaPartido)}</ul></section>
    <section><div class="cab"><h2>Líderes en puntos</h2><a href="#/estadisticas">Estadísticas</a></div>
      ${lideres?.jugadores.length ? html`<ul class="lista">${lideres.jugadores.map(l => html`<li style="grid-template-columns:32px 1fr auto"><span class="num" style="font-size:1.6rem;color:var(--suave)">${l.posicion}</span><a href="#/jugadores/${l.jugadorId}" style="font-weight:700;text-decoration:none">${jm.get(l.jugadorId)?.nombre || 'Jugador'}</a><span class="num" style="font-size:1.6rem">${fmt.num(l.valor)}</span></li>`)}</ul><p class="peq suave" style="margin-top:8px">Puntos por partido.</p>` : vacio('Las estadísticas aparecerán cuando se juegue el primer partido.')}</section>
  </div>

  <section><div class="cab"><h2>Últimas noticias</h2><a href="#/noticias">Todas las noticias</a></div>
    ${noticias.length ? html`<div class="grid2" style="gap:0 48px">${[...noticias].sort((a, b) => b.fechaPublicacion.localeCompare(a.fechaPublicacion)).slice(0, 4).map(n => resumenNoticia(n))}</div>` : vacio('Todavía no hay noticias.')}</section>

  ${patros.length ? html`<section><h2 style="margin-bottom:12px">Nos acompañan</h2><div class="patro">${patros.map(patro)}</div></section>` : ''}` };
}
