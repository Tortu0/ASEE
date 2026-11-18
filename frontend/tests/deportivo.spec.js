import { test, expect } from '@playwright/test';

test.describe('Microservicio Deportivo: Partidos, Plantilla y Noticias', () => {

    test('1. Listado de partidos y navegación al detalle de un encuentro', async ({ page }) => {
        await page.goto('/');

        // Navegamos desde el menú superior
        await page.locator('#nav').getByRole('link', { name: 'Partidos' }).click();
        await expect(page).toHaveURL(/#\/partidos$/);

        // Comprobamos los encabezados principales de partidos.js
        await expect(page.getByRole('heading', { name: 'Partidos', level: 1 })).toBeVisible();
        await expect(page.getByRole('heading', { name: 'Próximos', level: 2 })).toBeVisible();
        await expect(page.getByRole('heading', { name: 'Resultados', level: 2 })).toBeVisible();

        // Hacemos clic en el primer enlace que lleve a la ficha de un partido (#/partidos/...)
        const primerPartido = page.locator('a[href^="#/partidos/"]').first();
        await expect(primerPartido).toBeVisible();
        await primerPartido.click();

        // Verificamos que estamos dentro de la vista detalle(id)
        await expect(page).toHaveURL(/#\/partidos\/.+/);
        await expect(page.getByRole('link', { name: '← Todos los partidos' })).toBeVisible();
        await expect(page.getByRole('heading', { name: 'Estadísticas', level: 2 })).toBeVisible();
    });

    test('2. Listado de plantilla y acceso a la ficha individual de un jugador', async ({ page }) => {
        await page.goto('/#/plantilla');

        await expect(page.getByRole('heading', { name: 'Plantilla', level: 1 })).toBeVisible();
        await expect(page.locator('.cab')).toContainText('jugadores en activo');

        // Guardamos el nombre del primer jugador de la tarjeta .jug antes de hacer clic
        const primeraTarjeta = page.locator('a.jug').first();
        await expect(primeraTarjeta).toBeVisible();
        const nombreJugador = await primeraTarjeta.locator('strong').textContent();

        await primeraTarjeta.click();

        // Comprobamos que su ficha muestra el mismo nombre en el <h1> y el enlace de vuelta
        await expect(page).toHaveURL(/#\/jugadores\/.+/);
        await expect(page.getByRole('link', { name: '← Plantilla' })).toBeVisible();
        await expect(page.getByRole('heading', { name: nombreJugador, level: 1 })).toBeVisible();
    });

    test('3. Filtro por categorías en Noticias y lectura de un artículo', async ({ page }) => {
        await page.goto('/#/noticias');

        await expect(page.getByRole('heading', { name: 'Noticias', level: 1 })).toBeVisible();

        // Probamos la navegación por pestañas de categorías (ej. Crónicas)
        const tabCronicas = page.getByRole('navigation', { name: 'Categorías' }).getByRole('link', { name: 'Crónicas' });
        await tabCronicas.click();
        await expect(page).toHaveURL(/#\/noticias\?categoria=cronica/);
        await expect(tabCronicas).toHaveAttribute('aria-current', 'true');

        // Volvemos a "Todas" para asegurarnos de que haya noticias publicadas y abrimos la primera
        await page.getByRole('navigation', { name: 'Categorías' }).getByRole('link', { name: 'Todas' }).click();
        const primeraNoticia = page.locator('a[href^="#/noticias/"]').first();
        await expect(primeraNoticia).toBeVisible();
        await primeraNoticia.click();

        // Comprobamos que se renderiza el contenedor <article class="articulo">
        await expect(page).toHaveURL(/#\/noticias\/.+/);
        await expect(page.locator('article.articulo')).toBeVisible();
        await expect(page.getByRole('link', { name: '← Noticias' })).toBeVisible();
    });

    test('4. Muestra aviso de error controlado si el servicio Deportivo no responde', async ({ page }) => {
        // Interceptamos las peticiones al backend deportivo y simulamos un fallo de red
        await page.route('**/v1/partidos*', route => route.abort('failed'));

        await page.goto('/#/partidos');

        // main.js debe capturar el error y mostrar un aviso con role="alert" manteniendo la cabecera
        await expect(page.locator('#marca')).toBeVisible();
        await expect(page.locator('#app').getByRole('alert')).toBeVisible();
    });

});