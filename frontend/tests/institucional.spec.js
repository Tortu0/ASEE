import { test, expect } from '@playwright/test';

test.describe('Microservicio Institucional: Identidad Global y Vista del Club', () => {

    test('1. Carga la identidad oficial del club en la cabecera y el pie de página', async ({ page }) => {
        await page.goto('/');

        // tema() en main.js actualiza #marca y añade el año dinámico en #pie tras consultar /v1/club/identidad
        await expect(page.locator('#marca')).not.toBeEmpty();
        await expect(page.locator('#pie')).toContainText(/© \d{4}/);
    });

    test('2. Navega a El club y renderiza las seis secciones institucionales', async ({ page }) => {
        await page.goto('/');
        await expect(page.locator('#app h1')).toBeVisible();

        await page.locator('#nav').getByRole('link', { name: 'El club' }).click();
        await expect(page).toHaveURL(/#\/club$/);

        // El <h1> toma el nombre oficial del club
        await expect(page.locator('#app h1')).not.toBeEmpty();

        // Verificamos que los seis bloques principales de club.js se muestran en pantalla
        const secciones = [
            ['#historia', 'Historia'],
            ['#palmares', 'Palmarés'],
            ['#junta', 'Junta directiva'],
            ['#instalaciones', 'Instalaciones'],
            ['#cantera', 'Cantera'],
            ['#patrocinadores', 'Patrocinadores'],
        ];

        for (const [selector, titulo] of secciones) {
            await expect(page.locator(selector)).toBeVisible();
            await expect(page.locator(selector).getByRole('heading', { name: titulo, level: 2 })).toBeVisible();
        }
    });

    test('3. Mantiene el nombre de respaldo si cae el servicio y muestra alerta en #/club', async ({ page }) => {
        // Simulamos caída completa del microservicio Institucional
        await page.route('**/v1/club/**', route => route.abort('failed'));

        // Al abrir la portada, esperamos a que termine de renderizar antes de cambiar de vista
        await page.goto('/');
        await expect(page.locator('#app h1')).toBeVisible();
        await expect(page.locator('#marca')).toHaveText('CB Madroñera');

        // Al entrar en #/club, route() debe capturar el fallo y mostrar un aviso con role="alert"
        await page.goto('/#/club');
        await expect(page.locator('#app').getByRole('alert')).toBeVisible();
    });

});