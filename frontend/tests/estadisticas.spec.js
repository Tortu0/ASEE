import { test, expect } from '@playwright/test';

test.describe('Microservicio Estadísticas: Líderes, Tabla de Temporada y Resiliencia', () => {

    test('1. Carga la sección Estadísticas, la temporada activa y la tabla de jugadores', async ({ page }) => {
        await page.goto('/');
        await expect(page.locator('#app h1')).toBeVisible();

        // Navegamos desde el menú superior
        await page.locator('#nav').getByRole('link', { name: 'Estadísticas' }).click();
        await expect(page).toHaveURL(/#\/estadisticas$/);

        // Verificamos el encabezado principal y el indicador de temporada
        await expect(page.getByRole('heading', { name: 'Estadísticas', level: 1 })).toBeVisible();
        await expect(page.locator('.cab').first()).toContainText('Temporada');

        // Comprobamos que existen las dos secciones principales
        await expect(page.getByRole('heading', { name: 'Líderes', level: 2 })).toBeVisible();
        await expect(page.getByRole('heading', { name: 'Tabla de la temporada', level: 2 })).toBeVisible();

        // Verificamos las columnas y filas de la tabla de estadísticas
        const tabla = page.locator('table');
        if (await tabla.isVisible()) {
            await expect(tabla.locator('thead')).toContainText('Jugador');
            await expect(tabla.locator('thead')).toContainText('Pts');
            await expect(tabla.locator('thead')).toContainText('Val');
        } else {
            await expect(page.locator('.vacio').last()).toBeVisible();
        }
    });

    test('2. Cambiar la métrica en el desplegable de Líderes actualiza la URL y mantiene la selección', async ({ page }) => {
        await page.goto('/#/estadisticas');

        // Localizamos el <select id="met"> por su <label for="met">Estadística</label>
        const selectorMetrica = page.getByLabel('Estadística');
        await expect(selectorMetrica).toHaveValue('puntos_por_partido');

        // Cambiamos a "Rebotes por partido" y comprobamos la navegación por hash
        await selectorMetrica.selectOption('rebotes_por_partido');
        await expect(page).toHaveURL(/#\/estadisticas\?metrica=rebotes_por_partido/);
        await expect(page.getByLabel('Estadística')).toHaveValue('rebotes_por_partido');

        // Cambiamos también a "Valoración por partido"
        await page.getByLabel('Estadística').selectOption('valoracion_por_partido');
        await expect(page).toHaveURL(/#\/estadisticas\?metrica=valoracion_por_partido/);
        await expect(page.getByLabel('Estadística')).toHaveValue('valoracion_por_partido');
    });

    test('3. Si el servicio de Estadísticas cae, la página degrada sin romperse', async ({ page }) => {
        // Bloqueamos las consultas de rankings y temporada de Estadísticas (dejando pasar jugadores y club)
        await page.route(
            url => url.pathname.includes('/temporadas/'),
            route => route.abort('failed')
        );

        await page.goto('/#/estadisticas');

        // Gracias al .catch() en estadisticas.js, la página sigue operativa y muestra los estados vacíos
        await expect(page.getByRole('heading', { name: 'Estadísticas', level: 1 })).toBeVisible();
        await expect(page.locator('#app')).toContainText('Todavía no hay datos suficientes para este ranking.');
        await expect(page.locator('#app')).toContainText('La temporada aún no tiene estadísticas.');
    });

});
