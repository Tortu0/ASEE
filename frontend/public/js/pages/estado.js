import { api } from '../api.js';
import { html, aviso } from '../ui.js';
const NOTA = { ok: 'Responde según el contrato', contrato: 'Responde, pero NO como dice el contrato (ruta, prefijo /v1 o puerto)', caido: 'No responde' };
export default async function () {
  const s = await api.salud().catch(() => null);
  if (!s) return aviso('No se pudo consultar el gateway.', 'error');
  const ok = Object.values(s).filter(x => x.estado === 'ok').length;
  return { html: html`<div class="cab"><h1>Estado del sistema</h1><span class="suave">${ok} de ${Object.keys(s).length} servicios conformes</span></div>
    <p class="suave">El gateway lanza una petición de contrato a cada microservicio. Para una verificación completa de los flujos entre servicios, ejecuta <code>npm run verify</code>.</p>
    <div class="tabla-w"><table class="estado-tabla"><thead><tr><th>Servicio</th><th>Estado</th><th>HTTP</th><th>Latencia</th><th>Sonda</th></tr></thead><tbody>
    ${Object.entries(s).map(([k, v]) => html`<tr><td><b>${k}</b></td><td><span class="pto ${v.estado}"></span>${NOTA[v.estado]}${v.detalle ? ` (${v.detalle})` : ''}</td><td>${v.http || '–'}</td><td>${v.ms} ms</td><td><code>${v.sonda}</code></td></tr>`)}</tbody></table></div>` };
}
