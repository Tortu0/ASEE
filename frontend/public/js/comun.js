import { api, TEMPORADA_DEFECTO } from './api.js';
import { html, raw, fmt, ETQ } from './ui.js';

let cache = null;
export const jugadoresMapa = async () => cache ??= new Map((await api.jugadores()).map(j => [j.jugadorId, j]));
export const temporadaDe = ps => ps?.[0]?.temporada || TEMPORADA_DEFECTO;
// puntosLocal/puntosVisitante son los del equipo que juega en casa en ese partido
export const marcadorNuestro = p => p.puntosLocal == null ? null : p.esLocal ? [p.puntosLocal, p.puntosVisitante] : [p.puntosVisitante, p.puntosLocal];
export const etiqueta = e => html`<span class="etq ${e}">${ETQ[e] || e}</span>`;

export const PISTA = raw(`<svg class="pista" viewBox="0 0 500 470" fill="none" stroke="#fff" stroke-width="3" aria-hidden="true">
  <rect x="2" y="2" width="496" height="466"/><rect x="170" y="2" width="160" height="190"/><circle cx="250" cy="192" r="60"/>
  <path d="M32 2v138a218 218 0 0 0 436 0V2"/><path d="M215 40h70"/><circle cx="250" cy="54" r="9"/><path d="M170 80h-14M170 120h-14M330 80h14M330 120h14"/></svg>`);

export function filaPartido(p) {
  const m = marcadorNuestro(p);
  return html`<li class="partido-fila">
    <div class="fecha">${fmt.dia(p.fechaHora)}<br><span class="suave num" style="font-size:.95rem">${fmt.hora(p.fechaHora)}</span></div>
    <div><a class="rival" href="#/partidos/${p.partidoId}" style="text-decoration:none;color:inherit">${p.esLocal ? 'vs' : '@'} ${p.rival}</a><div class="peq suave">${p.lugar} · Jornada ${p.jornada ?? '–'}</div></div>
    <div class="res">${m ? html`<span style="color:${m[0] > m[1] ? 'var(--ok)' : 'var(--error)'}">${m[0]}–${m[1]}</span>` : etiqueta(p.estado)}${m && p.estado === 'en_juego' ? html` <span class="vivo"></span>` : ''}</div>
    ${p.ventaEntradasAbierta && p.estado === 'programado' ? html`<a class="btn compra chico" href="#/entradas/${p.partidoId}">Entradas</a>` : html`<a class="btn sec chico" href="#/partidos/${p.partidoId}">Ver</a>`}</li>`;
}
export const resumenNoticia = (n, grande = false) => html`<a class="noticia ${grande ? 'grande' : ''}" href="#/noticias/${n.noticiaId}"><span class="peq suave">${fmt.dia(n.fechaPublicacion)}</span><h3>${n.titulo}</h3><span class="suave">${n.contenido.length > 150 ? n.contenido.slice(0, 150) + '…' : n.contenido}</span></a>`;
