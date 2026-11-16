import { api, session } from '../api.js';
import { html, raw, fmt, vacio, aviso, modal, cerrarModal, toast, acts, forms } from '../ui.js';

const CAT = { camiseta: 'Camisetas', bufanda: 'Bufandas', gorra: 'Gorras', accesorio: 'Accesorios', otro: 'Otros' };
const ICONO = { camiseta: '<path d="M30 12 14 22l6 14 8-4v50h44V32l8 4 6-14-16-10c-3 8-9 12-14 12s-11-4-14-12Z"/>', gorra: '<path d="M14 62a36 36 0 0 1 72 0v6H14Zm0 0h72M60 62h38v6H60Z"/>', bufanda: '<path d="M32 14h36v44H32Zm4 44v28h10V58m10 0v22h10V58"/>' };
const icono = c => raw(`<svg viewBox="0 0 100 100" fill="currentColor" aria-hidden="true">${ICONO[c] || '<circle cx="50" cy="50" r="34" fill="none" stroke="currentColor" stroke-width="5"/><path d="M16 50h68M50 16c-14 14-14 54 0 68M50 16c14 14 14 54 0 68" fill="none" stroke="currentColor" stroke-width="4"/>'}</svg>`);
const stock = p => (p.stock || []).reduce((s, t) => s + t.unidades, 0);
let PRODS = new Map();

export default async function (_p, qs, path) { return path === '/carrito' ? carrito() : catalogo(qs.get('categoria') || ''); }

async function catalogo(cat) {
  const ps = await api.productos(cat ? { categoria: cat } : {}); PRODS = new Map(ps.map(p => [p.productoId, p]));
  return { html: html`<div class="cab"><h1>Tienda oficial</h1></div>
    <nav class="tabs" aria-label="Categorías"><a href="#/tienda" ${cat ? '' : 'aria-current="true"'}>Todo</a>${Object.entries(CAT).map(([k, t]) => html`<a href="#/tienda?categoria=${k}" ${cat === k ? 'aria-current="true"' : ''}>${t}</a>`)}</nav>
    ${ps.length ? html`<div class="catalogo">${ps.map(p => html`<button class="prod ${stock(p) ? '' : 'agotado'}" data-act="producto" data-id="${p.productoId}"><div class="foto">${p.fotos?.[0] ? html`<img src="${p.fotos[0]}" alt="">` : icono(p.categoria)}</div><div>${p.nombre}</div><div class="precio">${fmt.euros(p.precio)}</div><div class="peq suave">${stock(p) ? CAT[p.categoria] : 'Agotado'}</div></button>`)}</div>` : vacio('No hay productos en esta categoría.')}` };
}

acts.producto = el => {
  const p = PRODS.get(el.dataset.id);
  modal(html`<h2>${p.nombre}</h2><p class="suave" style="margin-top:8px">${p.descripcion || ''}</p><div class="total" style="margin:8px 0">${fmt.euros(p.precio)}</div>
    ${session.user ? html`<form data-form="añadir"><input type="hidden" name="productoId" value="${p.productoId}"><fieldset style="border:0;padding:0;margin:0 0 14px"><legend class="peq" style="font-weight:600;margin-bottom:6px">Talla</legend><div class="tallas">
      ${(p.stock || []).map((t, i) => html`<label><input type="radio" name="talla" value="${t.talla}" ${t.unidades ? '' : 'disabled'} required><span>${t.talla}</span></label>`)}</div></fieldset>
      <div class="campo" style="max-width:120px"><label for="cant">Cantidad</label><input id="cant" name="cantidad" type="number" min="1" value="1" required></div><div class="err"></div>
      <button class="btn compra" type="submit" ${stock(p) ? '' : 'disabled'}>${stock(p) ? 'Añadir al carrito' : 'Agotado'}</button></form>`
    : html`<a class="btn compra" href="#/entrar?volver=${encodeURIComponent('/tienda')}">Entra para comprar</a>`}`);
};
forms['añadir'] = async f => {
  const d = Object.fromEntries(new FormData(f)); const c = await api.añadir({ productoId: d.productoId, talla: d.talla, cantidad: +d.cantidad });
  cerrarModal(); toast(`Añadido. El carrito suma ${fmt.euros(c.total)}`);
};

async function carrito() {
  const c = await api.carrito(), u = session.user, vacioCarrito = !c.items.length;
  return { html: html`<div class="cab"><h1>Carrito</h1><a href="#/tienda">Seguir comprando</a></div>
    ${vacioCarrito ? vacio(html`Tu carrito está vacío. <a href="#/tienda">Ver la tienda</a>`) : html`<div class="dos"><div><div class="tabla-w"><table><thead><tr><th>Producto</th><th>Talla</th><th>Cantidad</th><th>Precio</th><th></th></tr></thead><tbody>
      ${c.items.map(i => html`<tr><td class="izq"><b>${i.nombre}</b></td><td>${i.talla ?? '–'}</td><td><label class="vh" for="q${i.itemId}">Cantidad</label><input id="q${i.itemId}" class="qty" type="number" min="1" value="${i.cantidad}" data-item="${i.itemId}" style="width:70px"></td><td>${fmt.euros(i.precioUnitario * i.cantidad)}</td><td><button class="btn peligro chico" data-act="quitar" data-id="${i.itemId}">Quitar</button></td></tr>`)}</tbody></table></div></div>
      <aside class="resumen"><h2>Resumen</h2><dl><div><dt>Subtotal</dt><dd>${fmt.euros(c.subtotal)}</dd></div>${c.descuentoSocio ? html`<div><dt>Descuento de socio</dt><dd>−${fmt.euros(c.descuentoSocio)}</dd></div>` : ''}</dl><div class="total">${fmt.euros(c.total)}</div>
      <form data-form="pedir" style="margin-top:14px"><div class="campo"><label for="dir">Dirección de envío</label><textarea id="dir" name="direccionEnvio" required style="min-height:70px">${u.direccion || ''}</textarea></div>
        <div class="campo"><label for="mp">Método de pago</label><select id="mp" name="metodoPago"><option value="tarjeta">Tarjeta</option><option value="bizum">Bizum</option><option value="paypal">PayPal</option></select></div><div class="err"></div>
        <button class="btn compra" style="width:100%" type="submit">Pagar ${fmt.euros(c.total)}</button></form>
      <button class="btn sec chico" style="margin-top:10px" data-act="vaciar">Vaciar carrito</button></aside></div>`}` };
}
const recargar = () => window.dispatchEvent(new HashChangeEvent('hashchange'));
acts.quitar = async el => { await api.quitar(el.dataset.id); recargar(); };
acts.vaciar = async () => { await api.vaciar(); recargar(); };
document.addEventListener('change', async e => { if (e.target.classList?.contains('qty')) { try { await api.cantidad(e.target.dataset.item, +e.target.value); } catch (err) { toast(err.message, 'error'); } recargar(); } });
// Idempotency-Key (contrato de Compras): la misma clave + los mismos datos devuelven el MISMO pedido sin cobrar otra vez.
// Si el pago queda "pendiente de conciliación" (503) o la red falla, al reintentar se reutiliza la clave; si el usuario
// cambia la dirección o el método de pago, es otra compra y se genera una clave nueva.
let intento = null;
forms.pedir = async f => {
  const datos = Object.fromEntries(new FormData(f));
  if (!intento || intento.direccion !== datos.direccionEnvio.trim() || intento.metodo !== datos.metodoPago) {
    intento = { clave: crypto.randomUUID(), direccion: datos.direccionEnvio.trim(), metodo: datos.metodoPago };
  }
  const p = await api.pedir(datos, intento.clave);
  intento = null;
  document.getElementById('app').innerHTML = html`<div class="aviso ok"><h2>Pedido realizado</h2><p style="margin-top:8px">Hemos registrado tu pedido <b>${p.pedidoId.slice(0, 8)}</b> por ${fmt.euros(p.total)}. Lo enviaremos a: ${p.direccionEnvio}.</p><a class="btn" href="#/cuenta/pedidos">Ver mis pedidos</a></div>`.s;
};
