// Genera los SVG de ejemplo (logos de patrocinadores, instalaciones y noticias) en public/img.
// Uso (desde la carpeta frontend):  node scripts/generar-imagenes.mjs
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const img = resolve(dirname(fileURLToPath(import.meta.url)), '../public/img');
for (const d of ['patrocinadores', 'club', 'noticias']) mkdirSync(resolve(img, d), { recursive: true });
const guardar = (ruta, txt) => writeFileSync(resolve(img, ruta), txt, 'utf-8');
const FUENTE = "'Big Shoulders Display','Arial Narrow',Impact,sans-serif";

// ── Logos de patrocinadores ──
const logos = {
    'banco-rural':      ['Banco Rural', '#1B7F47', '#E8F5EE', 'M30 62 L46 34 L62 62 Z'],
    'ferreteria-lopez': ['Ferretería López', '#C96F06', '#FEF3E4', 'M30 36 h32 v8 h-12 v20 h-8 v-20 h-12 z'],
    'bar-el-pabellon':  ['Bar El Pabellón', '#B93A2B', '#FBEDEB', 'M32 36 h28 l-4 28 h-20 z'],
    'talleres-ruiz':    ['Talleres Ruiz', '#34495E', '#E3E8EE', 'M46 34 a14 14 0 1 0 0.1 0 z M46 42 a6 6 0 1 1 -0.1 0 z'],
};
for (const [slug, [n, c, bg, icono]] of Object.entries(logos)) {
    guardar(`patrocinadores/${slug}.svg`, `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 280 100" role="img" aria-label="${n}">
<rect width="280" height="100" rx="10" fill="${bg}"/>
<circle cx="46" cy="50" r="26" fill="${c}"/>
<path d="${icono}" fill="#fff" fill-rule="evenodd"/>
<text x="86" y="57" font-family="${FUENTE}" font-weight="800" font-size="${n.length <= 14 ? 26 : 22}" fill="${c}">${n}</text>
</svg>`);
}

// ── Instalaciones (pista vista desde arriba) ──
const pista = (suelo, linea, extra = '') => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 450" role="img" aria-hidden="true">
<rect width="800" height="450" fill="${suelo}"/>${extra}
<g fill="none" stroke="${linea}" stroke-width="5" opacity=".9">
<rect x="40" y="40" width="720" height="370"/><line x1="400" y1="40" x2="400" y2="410"/><circle cx="400" cy="225" r="55"/>
<rect x="40" y="145" width="150" height="160"/><rect x="610" y="145" width="150" height="160"/>
<path d="M40 100 h95 a125 125 0 0 1 0 250 h-95"/><path d="M760 100 h-95 a125 125 0 0 0 0 250 h95"/>
<circle cx="190" cy="225" r="40"/><circle cx="610" cy="225" r="40"/></g></svg>`;
guardar('club/pabellon.svg', pista('#0B5FA5', '#fff', '<rect x="0" y="0" width="800" height="30" fill="#0C1F33" opacity=".35"/><rect x="0" y="420" width="800" height="30" fill="#0C1F33" opacity=".35"/>'));
guardar('club/pista-exterior.svg', pista('#C96F06', '#fff', '<rect width="800" height="450" fill="#000" opacity=".06"/>'));

// ── Imagen por categoría de noticia (se usa cuando la noticia no trae imagenUrl) ──
const cats = { fichaje: ['Fichajes', '#0B5FA5', '#0C1F33'], lesion: ['Lesiones', '#B93A2B', '#5b1d16'], convocatoria: ['Convocatorias', '#C96F06', '#6b3a03'],
    cronica: ['Crónicas', '#1B7F47', '#0e4527'], club: ['Club', '#34495E', '#0C1F33'], otra: ['Noticias', '#55657A', '#24303d'] };
for (const [k, [t, a, b]] of Object.entries(cats)) {
    guardar(`noticias/${k}.svg`, `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 450" role="img" aria-hidden="true">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient></defs>
<rect width="800" height="450" fill="url(#g)"/>
<g fill="none" stroke="#fff" stroke-width="4" opacity=".16"><circle cx="620" cy="225" r="170"/><circle cx="620" cy="225" r="95"/><path d="M450 225 h340"/><path d="M620 55 v340"/></g>
<text x="48" y="395" font-family="${FUENTE}" font-weight="800" font-size="64" fill="#fff" opacity=".92">${t.toUpperCase()}</text></svg>`);
}
console.log('Imágenes generadas en', img);