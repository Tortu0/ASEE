package com.asee.usuarios.integration;

import com.asee.usuarios.dto.ActividadEventDTO;
import com.asee.usuarios.entity.Actividad;
import com.asee.usuarios.entity.Rol;
import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.repository.ActividadRepository;
import com.asee.usuarios.repository.UsuarioRepository;
import com.asee.usuarios.service.ActividadListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private ActividadEventDTO evento(String referenciaId) {
        ActividadEventDTO evento = new ActividadEventDTO();
        evento.setUsuarioId(usuarioPrueba.getId());
        evento.setReferenciaId(referenciaId);
        return evento;
    }

    // =========================================================
    // 1. TESTS DE ESCRITURA (ASÍNCRONA VÍA RABBITMQ LISTENER)
    // El tipo de actividad se deduce de la routing key.
    // =========================================================

    @Test
    void recibirActividad_EntradaComprada_GuardaTipoEntradaConImporteYDescripcion() {
        ActividadEventDTO evento = evento("TICKET-001");
        evento.setImporte(new BigDecimal("7.50"));
        evento.setDescripcion("Entrada zona A, fila 3, asiento 12");
        evento.setFecha("2026-11-11T10:00:00Z");

        actividadListener.recibirActividad(evento, "entrada.comprada");

        List<Actividad> guardadas = actividadRepository.findByUsuarioIdOrderByFechaDesc(usuarioPrueba.getId());
        assertEquals(1, guardadas.size(), "La actividad debe haberse guardado en BD");
        assertEquals("entrada", guardadas.get(0).getTipo());
        assertEquals(0, new BigDecimal("7.50").compareTo(guardadas.get(0).getImporte()));
        assertEquals("Entrada zona A, fila 3, asiento 12", guardadas.get(0).getDescripcion());
    }

    @Test
    void recibirActividad_PedidoCreado_GuardaTipoPedido() {
        actividadListener.recibirActividad(evento("PED-123"), "pedido.creado");

        assertEquals("pedido", actividadRepository.findByUsuarioIdOrderByFechaDesc(usuarioPrueba.getId()).get(0).getTipo());
    }

    @Test
    void recibirActividad_EventoDuplicado_IdempotenciaLoIgnoraSilenciosamente() {
        ActividadEventDTO evento = evento("RES-999");

        // Simulamos que RabbitMQ entrega el MISMO mensaje DOS VECES
        actividadListener.recibirActividad(evento, "pedido.creado");
        actividadListener.recibirActividad(evento, "pedido.creado");

        assertEquals(1, actividadRepository.count(), "El duplicado debió ser ignorado");
    }

    @Test
    void recibirActividad_RoutingKeyDesconocida_RechazaSinReencolar() {
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> actividadListener.recibirActividad(evento("X-1"), "otra.cosa"));
        assertEquals(0, actividadRepository.count());
    }

    @Test
    void recibirActividad_UsuarioInexistente_SeDescartaSinGuardar() {
        ActividadEventDTO evento = evento("TICKET-404");
        evento.setUsuarioId(UUID.randomUUID());

        actividadListener.recibirActividad(evento, "entrada.comprada");

        assertEquals(0, actividadRepository.count());
    }

    // =========================================================
    // 2. TESTS DE LECTURA (SÍNCRONA VÍA CONTROLADOR HTTP)
    // =========================================================

    @Test
    void consultarHistorial_UsuarioConActividades_Devuelve200YListaJSON() throws Exception {
        ActividadEventDTO evento = evento("MERCH-555");
        evento.setImporte(new BigDecimal("49.95"));
        evento.setDescripcion("Pedido de 1 artículo");
        actividadListener.recibirActividad(evento, "pedido.creado");

        mockMvc.perform(get("/usuarios/" + usuarioPrueba.getId() + "/historial")
                        .header("X-User-Id", usuarioPrueba.getId().toString()) // Mismo usuario
                        .header("X-User-Role", "socio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("pedido"))
                .andExpect(jsonPath("$[0].referenciaId").value("MERCH-555"))
                .andExpect(jsonPath("$[0].descripcion").value("Pedido de 1 artículo"))
                .andExpect(jsonPath("$[0].importe").value(49.95));
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

    // =========================================================
    // 3. POST /usuarios/{id}/historial (solo admin)
    // =========================================================

    private static final String CUERPO_VALIDO =
            "{\"tipo\":\"entrada\",\"referenciaId\":\"REF-1\",\"fecha\":\"2026-11-11T10:00:00Z\",\"importe\":5.5,\"descripcion\":\"Alta manual\"}";

    @Test
    void registrarActividad_ComoAdmin_Devuelve201() throws Exception {
        mockMvc.perform(post("/usuarios/" + usuarioPrueba.getId() + "/historial")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("entrada"))
                .andExpect(jsonPath("$.referenciaId").value("REF-1"));

        assertEquals(1, actividadRepository.count());
    }

    @Test
    void registrarActividad_ComoStaff_Devuelve403() throws Exception {
        mockMvc.perform(post("/usuarios/" + usuarioPrueba.getId() + "/historial")
                        .header("X-User-Role", "staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrarActividad_TipoInvalido_Devuelve400() throws Exception {
        mockMvc.perform(post("/usuarios/" + usuarioPrueba.getId() + "/historial")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO.replace("\"entrada\"", "\"otro\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrarActividad_Duplicada_Devuelve409() throws Exception {
        for (int i = 0; i < 2; i++) {
            var r = mockMvc.perform(post("/usuarios/" + usuarioPrueba.getId() + "/historial")
                    .header("X-User-Role", "admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(CUERPO_VALIDO));
            r.andExpect(i == 0 ? status().isCreated() : status().isConflict());
        }
    }

    @Test
    void registrarActividad_UsuarioNoExiste_Devuelve404() throws Exception {
        mockMvc.perform(post("/usuarios/" + UUID.randomUUID() + "/historial")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isNotFound());
    }
}
