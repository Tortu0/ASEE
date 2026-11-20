import { api } from '../api.js';
import { html, fmt, vacio, imagen, urlSegura, iniciales, acts } from '../ui.js';
import { patro } from '../comun.js';

const NIVEL = ['principal', 'oro', 'plata', 'bronce', 'colaborador'];
const SECC = [['historia', 'Historia'], ['palmares', 'Palmarés'], ['junta', 'Junta'], ['instalaciones', 'Instalaciones'], ['cantera', 'Cantera'], ['patrocinadores', 'Patrocinadores']];
const foto = i => /pista|exterior/i.test(i.nombre) ? '/img/club/pista-exterior.svg' : '/img/club/pabellon.svg';
const mapa = i => i.latitud != null && i.longitud != null
    ? `https://www.openstreetmap.org/?mlat=${i.latitud}&mlon=${i.longitud}#map=17/${i.latitud}/${i.longitud}`
    : `https://www.openstreetmap.org/search?query=${encodeURIComponent(i.direccion)}`;

// Los enlaces internos no pueden ser "#seccion": el router usa el hash. Se desplaza a la sección.
acts.ir = el => { const d = document.getElementById(el.dataset.a); if (!d) return; const reducir = matchMedia('(prefers-reduced-motion: reduce)').matches; d.scrollIntoView({ behavior: reducir ? 'auto' : 'smooth', block: 'start' }); };

export default async function () {
  const [h, junta, inst, pal, cant, pat] = await Promise.all([api.historia(), api.junta(), api.instalaciones(), api.palmares(), api.cantera(), api.patrocinadores()]);
  const nombre = window.CLUB?.nombreOficial || 'El club', escudo = urlSegura(window.CLUB?.escudoUrl);
  const anios = h.fechaFundacion ? new Date().getFullYear() - new Date(h.fechaFundacion).getFullYear() : null;
  const aforo = inst.reduce((m, i) => Math.max(m, i.capacidad || 0), 0);
  const datos = [[anios, 'años de historia'], [pal.length, 'títulos en el palmarés'], [cant.length, 'categorías de cantera'], [aforo || null, 'personas de aforo']].filter(([v]) => v);
  const sinTexto = !h.texto?.trim();
  return { html: html`<div class="cab"><h1>${escudo ? html`<img class="escudo" src="${escudo}" alt="" style="height:1em;vertical-align:-.12em;margin-right:12px">` : ''}${nombre}</h1>
    <nav class="tabs" style="border:0" aria-label="Secciones del club">${SECC.map(([k, t]) => html`<button type="button" data-act="ir" data-a="${k}">${t}</button>`)}</nav></div>
  ${datos.length ? html`<ul class="cifras" aria-label="El club en cifras">${datos.map(([v, t]) => html`<li><b class="num">${v}</b><span>${t}</span></li>`)}</ul>` : ''}

  <section id="historia" class="dos"><div><h2 style="margin-bottom:12px">Historia</h2>${sinTexto ? vacio('La historia del club aún no está publicada.') : h.texto.split(/\n+/).map(t => html`<p>${t}</p>`)}${h.fechaFundacion ? html`<p class="suave">Fundado el ${fmt.diaLargo(h.fechaFundacion)}.</p>` : ''}</div>
    ${h.hitos?.length ? html`<ol class="hitos" aria-label="Hitos">${[...h.hitos].sort((a, b) => a.anio - b.anio).map(x => html`<li><b>${x.anio}</b>${x.descripcion}</li>`)}</ol>` : ''}</section>

  <section id="palmares"><h2 style="margin-bottom:12px">Palmarés</h2>${pal.length ? html`<div class="tarjetas">${pal.map(t => html`<article class="tarjeta">${urlSegura(t.fotoUrl) ? imagen(t.fotoUrl, '', '', 'tarjeta-img') : ''}<span class="num" style="font-size:1.4rem;color:var(--azul)">${t.temporada}</span><h3>${t.competicion}</h3>${t.descripcion ? html`<p class="suave" style="margin:0">${t.descripcion}</p>` : ''}</article>`)}</div>` : vacio('Aún no hay títulos registrados.')}</section>

  <section id="junta"><h2 style="margin-bottom:12px">Junta directiva</h2>${junta.length ? html`<ul class="junta">${junta.map(m => html`<li>${urlSegura(m.fotoUrl) ? imagen(m.fotoUrl, '', '', 'avatar') : html`<span class="avatar" aria-hidden="true">${iniciales(m.nombre)}</span>`}<div><b>${m.nombre}</b><br><span class="suave">${m.cargo}</span></div></li>`)}</ul>` : vacio('La junta directiva aún no está publicada.')}</section>

  <section id="instalaciones"><h2 style="margin-bottom:12px">Instalaciones</h2>${inst.length ? html`<div class="tarjetas">${inst.map(i => html`<article class="tarjeta">${imagen(i.fotos?.[0], i.nombre, foto(i), 'tarjeta-img')}<h3>${i.nombre}</h3>
      <p class="suave" style="margin:0">${i.direccion}${i.capacidad ? ` · aforo ${i.capacidad} personas` : ''}</p>${i.comoLlegar ? html`<p style="margin:0">${i.comoLlegar}</p>` : ''}
      <a href="${mapa(i)}" target="_blank" rel="noopener noreferrer">Ver en el mapa</a></article>`)}</div>` : vacio('Instalaciones aún sin publicar.')}</section>

  <section id="cantera"><h2 style="margin-bottom:6px">Cantera</h2><p class="suave">Baloncesto para todas las edades. Para inscribirte o resolver dudas, habla con la secretaría del club.</p>
    ${cant.length ? html`<div class="tabla-w"><table><thead><tr><th class="izq">Categoría</th><th class="izq">Edades</th><th class="izq">Entrenador/a</th><th class="izq">Horarios</th></tr></thead><tbody>${[...cant].sort((a, b) => parseInt(a.edades) - parseInt(b.edades)).map(c => html`<tr><td class="izq"><b>${c.nombre}</b></td><td class="izq">${c.edades ?? '–'}</td><td class="izq">${c.entrenador ?? 'Por confirmar'}</td><td class="izq">${c.horarios ?? 'Por confirmar'}</td></tr>`)}</tbody></table></div>` : vacio('Sin categorías publicadas.')}</section>

  <section id="patrocinadores"><h2 style="margin-bottom:6px">Patrocinadores</h2><p class="suave">Gracias a ellos el club sigue creciendo.</p>${pat.length ? NIVEL.map(n => [n, pat.filter(p => p.nivel === n)]).filter(([, l]) => l.length).map(([n, l]) => html`<div class="patro"><span class="peq suave" style="width:110px">${n[0].toUpperCase() + n.slice(1)}</span>${l.map(patro)}</div>`) : vacio('Aún no hay patrocinadores.')}</section>` };
}