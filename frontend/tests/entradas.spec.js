import { test, expect } from '@playwright/test';

// Función auxiliar para registrar un usuario limpio antes de probar compras
async function registrarUsuarioLimpio(page) {
    const email = `aficionado.${Date.now()}@club.test`;
    await page.goto('/#/entrar?tab=registro');
    await page.getByLabel('Nombre completo').fill('Aficionado Test');
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Contraseña').fill('ClaveSegura123');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();
    await expect(page).toHaveURL(/#\/cuenta/);
}

test.describe('Microservicio Entradas: Mapa de asientos, Reservas y Compra con QR', () => {

    test('1. Selección de asiento sin sesión pide iniciar sesión guardando la vuelta', async ({ page }) => {
        await page.goto('/#/partidos');

        // Entramos en la taquilla del primer partido que tenga venta abierta
        const btnEntradas = page.locator('a[href^="#/entradas/"]').first();
        await expect(btnEntradas).toBeVisible();
        await btnEntradas.click();

        // Comprobamos que se carga la cabecera y la pista
        await expect(page).toHaveURL(/#\/entradas\/.+/);
        await expect(page.getByRole('heading', { name: 'Entradas', level: 1 })).toBeVisible();
        await expect(page.locator('.cancha')).toHaveText('Pista');

        // Seleccionamos el primer asiento libre (botón .seat que no está disabled)
        const asientoLibre = page.locator('button.seat:not([disabled])').first();
        await expect(asientoLibre).toBeVisible();
        await asientoLibre.click();

        // Verificamos que queda marcado con la clase 'sel', aparece la zona en el resumen y se calcula el total en euros
        await expect(asientoLibre).toHaveClass(/sel/);
        await expect(page.locator('#panel')).toContainText('Zona ');
        await expect(page.locator('#panel .total')).toContainText(/€/);

        // Como no hay sesión, debe mostrar el enlace "Entra para reservar"
        const linkEntrar = page.locator('#panel').getByRole('link', { name: 'Entra para reservar' });
        await expect(linkEntrar).toBeVisible();
        await linkEntrar.click();
        await expect(page).toHaveURL(/#\/entrar\?volver=%2Fentradas%2F/);
    });

    test('2. Reservar un asiento y cancelar la reserva libera el panel', async ({ page }) => {
        await registrarUsuarioLimpio(page);

        // Navegamos desde el menú superior para no recargar la página completa
        await page.locator('#nav').getByRole('link', { name: 'Partidos' }).click();
        await page.locator('a[href^="#/entradas/"]').first().click();

        // Seleccionamos un asiento libre y pulsamos "Reservar 1 asiento"
        await page.locator('button.seat:not([disabled])').first().click();
        await page.locator('#panel').getByRole('button', { name: /Reservar 1 asiento/ }).click();

        // El panel debe pasar al estado "Pagar" con la cuenta atrás activa
        await expect(page.locator('#panel').getByRole('heading', { name: 'Pagar', level: 2 })).toBeVisible();
        await expect(page.locator('#atras')).toBeVisible();

        // Cancelamos la reserva y verificamos el toast y la vuelta a "Tu selección"
        await page.locator('#panel').getByRole('button', { name: 'Cancelar reserva' }).click();
        await expect(page.locator('#toasts')).toContainText('Reserva cancelada');
        await expect(page.locator('#panel').getByRole('heading', { name: 'Tu selección', level: 2 })).toBeVisible();
    });

    test('3. Flujo completo de compra genera la entrada con QR y la guarda en Mi cuenta', async ({ page }) => {
        await registrarUsuarioLimpio(page);

        // Navegamos desde el menú superior para no recargar la página completa
        await page.locator('#nav').getByRole('link', { name: 'Partidos' }).click();
        await page.locator('a[href^="#/entradas/"]').first().click();

        // 1. Seleccionar asiento y reservar
        await page.locator('button.seat:not([disabled])').first().click();
        await page.locator('#panel').getByRole('button', { name: /Reservar 1 asiento/ }).click();
        await expect(page.locator('#panel').getByRole('heading', { name: 'Pagar', level: 2 })).toBeVisible();

        // 2. Elegir Bizum y confirmar el pago
        await page.getByLabel('Bizum').check();
        await page.locator('form[data-form="comprar"] button[type="submit"]').click();

        // 3. Verificar que se genera el ticket con su SVG del código QR
        await expect(page.locator('#toasts')).toContainText('Compra completada');
        await expect(page.locator('#panel').getByRole('heading', { name: '¡Entradas listas!', level: 2 })).toBeVisible();
        await expect(page.locator('#panel .entrada .qr svg')).toBeVisible();

        // 4. Comprobar que la entrada también aparece en el perfil del usuario (#/cuenta/entradas)
        await page.goto('/#/cuenta/entradas');
        await expect(page.locator('.entrada .qr svg')).toBeVisible();
    });

    test('4. Muestra aviso controlado si el servicio de Entradas falla al cargar asientos', async ({ page }) => {
        await page.goto('/#/partidos');
        const hrefTaquilla = await page.locator('a[href^="#/entradas/"]').first().getAttribute('href');

        // Simulamos caída en la petición de asientos
        await page.route('**/asientos*', route => route.abort('failed'));

        await page.goto('/' + hrefTaquilla);
        await expect(page.locator('#marca')).toBeVisible();
        await expect(page.locator('#app').getByRole('alert')).toBeVisible();
    });

});