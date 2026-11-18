import { api } from '../api.js';
import { html, raw, fmt, vacio, CAT, acts } from '../ui.js';
import { resumenNoticia, jugadoresMapa } from '../comun.js';

export default async function ({ id }, qs) {
  if (id) {
    const [n, jm] = await Promise.all([api.noticia(id), jugadoresMapa()]);
    return { html: html`<p><a href="#/noticias">← Noticias</a></p><article class="articulo"><span class="etq">${CAT[n.categoria] || n.categoria}</span><h1 style="margin:12px 0 8px">${n.titulo}</h1><p class="suave peq">${fmt.diaLargo(n.fechaPublicacion)}</p>
        ${n.imagenUrl ? html`<img src="${n.imagenUrl}" alt="" style="max-width:100%;border-radius:6px;margin-bottom:16px">` : ''}${n.contenido.split(/\n+/).map(t => html`<p>${t}</p>`)}
        ${n.jugadoresRelacionados?.length ? html`<p class="peq">Jugadores: ${n.jugadoresRelacionados.map(j => jm.get(j) ? html`<a href="#/jugadores/${j}">${jm.get(j).nombre}</a> ` : '')}</p>` : ''}</article>` };
  }
  const cat = qs.get('categoria') || '', ns = (await api.noticias(cat ? { categoria: cat } : {})).sort((a, b) => b.fechaPublicacion.localeCompare(a.fechaPublicacion));
  return { html: html`<div class="cab"><h1>Noticias</h1></div>
    <nav class="tabs" aria-label="Categorías"><a href="#/noticias" ${cat ? '' : raw('aria-current="true"')}>Todas</a>${Object.entries(CAT).map(([k, t]) => html`<a href="#/noticias?categoria=${k}" ${cat === k ? raw('aria-current="true"') : ''}>${t}</a>`)}</nav>
    ${ns.length ? html`<div>${resumenNoticia(ns[0], true)}<div class="grid2" style="gap:0 48px">${ns.slice(1).map(n => resumenNoticia(n))}</div></div>` : vacio('No hay noticias en esta categoría.')}` };
}