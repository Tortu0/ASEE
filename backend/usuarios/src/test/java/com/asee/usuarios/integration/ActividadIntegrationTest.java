package com.asee.usuarios.integration;

import com.asee.usuarios.dto.ActividadEventDTO;
import com.asee.usuarios.entity.Rol;
import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.repository.ActividadRepository;
import com.asee.usuarios.repository.UsuarioRepository;
import com.asee.usuarios.service.ActividadListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
public class ActividadIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActividadListener actividadListener;

    @Autowired
    private ActividadRepository actividadRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario usuarioPrueba;

    @BeforeEach
    void setUp() {
        // Limpiamos las tablas (el orden importa por las claves foráneas)
        actividadRepository.deleteAll();
        usuarioRepository.deleteAll();

        // Creamos un usuario real en la BBDD para poder asignarle actividades
        Usuario u = new Usuario();
        u.setNombre("Socio Activo");
        u.setEmail("actividades@correo.com");
        u.setPassword("hash");
        u.setTelefono("123");
        u.setRol(Rol.socio);
        usuarioPrueba = usuarioRepository.save(u);
    }

    // =========================================================
    // 1. TESTS DE ESCRITURA (ASÍNCRONA VÍA RABBITMQ LISTENER)
    // =========================================================

    @Test
    void recibirActividad_EventoNuevo_GuardaEnBBDD() {
        ActividadEventDTO evento = new ActividadEventDTO();
        evento.setUsuarioId(usuarioPrueba.getId());
        evento.setTipo("COMPRA_ENTRADA");
        evento.setReferenciaId("TICKET-001");

        actividadListener.recibirActividad(evento);

        assertEquals(1, actividadRepository.count(), "La actividad debe haberse guardado en BD");
    }

    @Test
    void recibirActividad_EventoDuplicado_IdempotenciaLoIgnoraSilenciosamente() {
        ActividadEventDTO evento = new ActividadEventDTO();
        evento.setUsuarioId(usuarioPrueba.getId());
        evento.setTipo("RESERVA_PISTA");
        evento.setReferenciaId("RES-999");

        // Simulamos que RabbitMQ entrega el MISMO mensaje DOS VECES
        actividadListener.recibirActividad(evento);
        actividadListener.recibirActividad(evento);

        // Verificamos que la Base de Datos bloqueó el segundo (Unique Constraint) y el catch funcionó
        assertEquals(1, actividadRepository.count(), "El duplicado debió ser ignorado por la restricción UNIQUE");
    }

    // =========================================================
    // 2. TESTS DE LECTURA (SÍNCRONA VÍA CONTROLADOR HTTP)
    // =========================================================

    @Test
    void consultarHistorial_UsuarioConActividades_Devuelve200YListaJSON() throws Exception {
        ActividadEventDTO evento = new ActividadEventDTO();
        evento.setUsuarioId(usuarioPrueba.getId());
        evento.setTipo("COMPRA_MERCHANDISING");
        evento.setReferenciaId("MERCH-555");
        actividadListener.recibirActividad(evento);

        mockMvc.perform(get("/usuarios/" + usuarioPrueba.getId() + "/historial")
                        .header("X-User-Id", usuarioPrueba.getId().toString()) // Mismo usuario
                        .header("X-User-Role", "socio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("COMPRA_MERCHANDISING"))
                .andExpect(jsonPath("$[0].referenciaId").value("MERCH-555"));
    }

    @Test
    void consultarHistorial_OtroUsuarioSinPermisos_Devuelve403Forbidden() throws Exception {
        UUID idAtacante = UUID.randomUUID();

        mockMvc.perform(get("/usuarios/" + usuarioPrueba.getId() + "/historial")
                        .header("X-User-Id", idAtacante.toString()) // IDs no coinciden
                        .header("X-User-Role", "visitante")) // No es admin ni staff
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tienes permisos para consultar este historial"));
    }

    @Test
    void consultarHistorial_UsuarioNoExiste_Devuelve404NotFound() throws Exception {
        UUID idInexistente = UUID.randomUUID();

        mockMvc.perform(get("/usuarios/" + idInexistente + "/historial")
                        .header("X-User-Id", idInexistente.toString()) // El usuario pide su propio historial
                        .header("X-User-Role", "visitante"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Usuario no encontrado"));
    }
}