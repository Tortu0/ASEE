import { test, expect } from '@playwright/test';

async function registrarUsuarioLimpio(page) {
    const email = `comprador.${Date.now()}@club.test`;
    await page.goto('/#/entrar?tab=registro');
    await page.getByLabel('Nombre completo').fill('Comprador Test');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Contraseña').fill('ClaveSegura123');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();
    await expect(page).toHaveURL(/#\/cuenta/);
}

test.describe('Microservicio Compras: Catálogo, Carrito y Pedidos', () => {

    test('1. Carga la tienda, filtra por categorías y protege el acceso al carrito sin sesión', async ({ page }) => {
        await page.goto('/');

        // Navegamos a la tienda desde el menú superior
        await page.locator('#nav').getByRole('link', { name: 'Tienda' }).click();
        await expect(page).toHaveURL(/#\/tienda$/);
        await expect(page.getByRole('heading', { name: 'Tienda oficial', level: 1 })).toBeVisible();

        // Probamos el filtro por categoría (ej. Camisetas)
        const tabCamisetas = page.getByRole('navigation', { name: 'Categorías' }).getByRole('link', { name: 'Camisetas' });
        await tabCamisetas.click();
        await expect(page).toHaveURL(/#\/tienda\?categoria=camiseta/);

        // Intentamos entrar directamente al carrito sin haber iniciado sesión
        await page.goto('/#/carrito');
        await expect(page).toHaveURL(/#\/entrar\?volver=%2Fcarrito/);
    });

    test('2. Abrir un producto sin sesión muestra el modal con el aviso Entra para comprar', async ({ page }) => {
        await page.goto('/#/tienda');

        const primerProducto = page.locator('button.prod').first();
        test.skip((await primerProducto.count()) === 0, 'Catálogo vacío hasta que el servicio Compras publique productos');

        await primerProducto.click();

        // Comprobamos que se abre el <dialog id="modal"> nativo
        const modal = page.locator('#modal');
        await expect(modal).toBeVisible();
        await expect(modal.getByRole('link', { name: 'Entra para comprar' })).toBeVisible();
    });

    test('3. Flujo completo: añadir producto con talla al carrito y finalizar pedido', async ({ page }) => {
        await registrarUsuarioLimpio(page);
        await page.goto('/#/tienda');

        // Buscamos un producto que no tenga la clase 'agotado'
        const productoDisponible = page.locator('button.prod:not(.agotado)').first();
        test.skip((await productoDisponible.count()) === 0, 'Pendiente de que el microservicio Compras devuelva productos con stock');

        // 1. Abrimos el modal del producto
        await productoDisponible.click();
        const modal = page.locator('#modal');
        await expect(modal).toBeVisible();

        // 2. Seleccionamos la primera talla disponible y pulsamos "Añadir al carrito"
        await modal.locator('.tallas label').filter({ has: page.locator('input:not([disabled])') }).first().click();
        await modal.getByRole('button', { name: 'Añadir al carrito' }).click();

        // 3. Verificamos el toast y entramos en el carrito desde la cabecera
        await expect(page.locator('#toasts')).toContainText('Añadido. El carrito suma');
        await page.locator('#cuenta').getByRole('link', { name: 'Carrito' }).click();
        await expect(page).toHaveURL(/#\/carrito/);
        await expect(page.getByRole('heading', { name: 'Carrito', level: 1 })).toBeVisible();

        // 4. Rellenamos dirección de envío y confirmamos el pedido
        await page.getByLabel('Dirección de envío').fill('Calle Mayor 12, Cáceres');
        await page.locator('form[data-form="pedir"] button[type="submit"]').click();

        // 5. Verificamos la confirmación del pedido
        await expect(page.getByRole('heading', { name: 'Pedido realizado', level: 2 })).toBeVisible();
        await expect(page.getByRole('link', { name: 'Ver mis pedidos' })).toBeVisible();
    });

    test('4. Muestra aviso controlado si el microservicio Compras no responde', async ({ page }) => {
        await page.route('**/productos*', route => route.abort('failed'));

        await page.goto('/#/tienda');
        await expect(page.locator('#marca')).toBeVisible();
        await expect(page.locator('#app').getByRole('alert')).toBeVisible();
    });

});