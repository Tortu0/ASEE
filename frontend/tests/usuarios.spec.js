import { test, expect } from '@playwright/test';

test.describe('Microservicio Usuarios: Identidad, Perfil y Seguridad', () => {

    test('1. Registro de un nuevo usuario y acceso automático a Mi cuenta', async ({ page }) => {
        const emailUnico = `elena.${Date.now()}@club.test`;

        // 1. Vamos a la pestaña de registro
        await page.goto('/#/entrar?tab=registro');

        // 2. Rellenamos los campos buscando por su etiqueta <label> visible
        await page.getByLabel('Nombre completo').fill('Elena García');
        await page.getByLabel('Email').fill(emailUnico);
        await page.getByLabel('Contraseña').fill('ClaveSegura123');

        // 3. Enviamos el formulario
        await page.getByRole('button', { name: 'Crear cuenta' }).click();

        // 4. Comprobamos que redirige a #/cuenta, muestra su email, rol visitante y su nombre arriba
        await expect(page).toHaveURL(/#\/cuenta/);
        await expect(page.getByRole('heading', { name: 'Mi cuenta' })).toBeVisible();
        await expect(page.locator('.cab')).toContainText(emailUnico);
        await expect(page.locator('.cab')).toContainText('visitante');
        await expect(page.locator('#cuenta')).toContainText('Elena');
    });

    test('2. Muestra alerta de error si las credenciales de login son incorrectas', async ({ page }) => {
        await page.goto('/#/entrar');

        await page.getByLabel('Email').fill('admin@club.test');
        await page.getByLabel('Contraseña').fill('ContrasenaFalsa999');
        await page.getByRole('button', { name: 'Entrar' }).click();

        // Comprobamos que ui.js pinta el contenedor con role="alert" dentro del formulario
        const alertaError = page.locator('form[data-form="login"]').getByRole('alert');
        await expect(alertaError).toBeVisible();
        // Y que seguimos en /entrar sin haber iniciado sesión
        await expect(page).toHaveURL(/#\/entrar/);
    });

    test('3. Login como administrador habilita el menú privado de Gestión', async ({ page }) => {
        await page.goto('/#/entrar');

        await page.getByLabel('Email').fill('admin@club.test');
        await page.getByLabel('Contraseña').fill('Club2026!');
        await page.getByRole('button', { name: 'Entrar' }).click();

        // Verificamos la cabecera de Mi cuenta y que main.js añade "Gestión" al menú #nav
        await expect(page.locator('.cab')).toContainText('admin');
        await expect(page.locator('#nav').getByRole('link', { name: 'Gestión' })).toBeVisible();
    });

    test('4. Edición de perfil y persistencia real tras recargar la página', async ({ page }) => {
        const emailUnico = `perfil.${Date.now()}@club.test`;
        const telefonoNuevo = '600123456';
        const direccionNueva = 'Av. de la Universidad s/n, Cáceres';

        // Registramos un usuario limpio para no ensuciar la cuenta del admin
        await page.goto('/#/entrar?tab=registro');
        await page.getByLabel('Nombre completo').fill('Usuario Perfil');
        await page.getByLabel('Email').fill(emailUnico);
        await page.getByLabel('Contraseña').fill('ClaveSegura123');
        await page.getByRole('button', { name: 'Crear cuenta' }).click();
        await expect(page).toHaveURL(/#\/cuenta/);

        // Editamos teléfono y dirección en el formulario de Perfil
        await page.getByLabel('Teléfono').fill(telefonoNuevo);
        await page.getByLabel('Dirección').fill(direccionNueva);
        await page.getByRole('button', { name: 'Guardar cambios' }).click();

        // Comprobamos que aparece el toast de confirmación de ui.js
        await expect(page.locator('#toasts')).toContainText('Cambios guardados');

        // Recargamos el navegador completo (F5) para demostrar que se leyó desde PostgreSQL
        await page.reload();
        await expect(page.getByLabel('Teléfono')).toHaveValue(telefonoNuevo);
        await expect(page.getByLabel('Dirección')).toHaveValue(direccionNueva);
    });

    test('5. Cierre de sesión y redirección al intentar acceder a rutas protegidas', async ({ page }) => {
        // Iniciamos sesión
        await page.goto('/#/entrar');
        await page.getByLabel('Email').fill('admin@club.test');
        await page.getByLabel('Contraseña').fill('Club2026!');
        await page.getByRole('button', { name: 'Entrar' }).click();
        await expect(page.locator('#cuenta')).toContainText('Salir');

        // Pulsamos el botón "Salir" (acts.salir en main.js)
        await page.locator('#cuenta').getByRole('button', { name: 'Salir' }).click();

        // Comprobamos que vuelve al inicio y desaparece el menú Gestión
        await expect(page).toHaveURL(/#\//);
        await expect(page.locator('#cuenta')).toContainText('Entrar');
        await expect(page.locator('#nav').getByRole('link', { name: 'Gestión' })).toBeHidden();

        // Intentamos entrar a la fuerza en #/cuenta sin token: main.js debe redirigir a #/entrar
        await page.goto('/#/cuenta');
        await expect(page).toHaveURL(/#\/entrar\?volver=%2Fcuenta/);
    });

});