package com.asee.usuarios.integration;

import com.asee.usuarios.dto.SocioUpdateDTO;
import com.asee.usuarios.entity.Rol;
import com.asee.usuarios.entity.Socio;
import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.repository.SocioRepository;
import com.asee.usuarios.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional // Garantiza que Hibernate comparta el contexto de persistencia durante la prueba
public class SocioControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Usuario usuarioPrueba;

    @BeforeEach
    void setUp() {
        // Limpiamos las tablas (importa el orden por las claves foráneas)
        socioRepository.deleteAll();
        usuarioRepository.deleteAll();

        // Preparamos un usuario base para las pruebas
        Usuario u = new Usuario();
        u.setNombre("Test Socio");
        u.setEmail("socio@correo.com");
        u.setPassword("hash");
        u.setTelefono("123");
        u.setRol(Rol.visitante);
        usuarioPrueba = usuarioRepository.save(u);
    }

    // ==========================================
    // TESTS PARA: GET /usuarios/{id}/socio (Leer estado)
    // ==========================================

    @Test
    void obtenerSocio_MismoUsuarioNoEsSocio_DevuelveEstadoPorDefecto() throws Exception {
        mockMvc.perform(get("/usuarios/" + usuarioPrueba.getId() + "/socio")
                        .header("X-User-Id", usuarioPrueba.getId().toString())
                        .header("X-User-Role", "visitante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("no_socio"))
                .andExpect(jsonPath("$.descuentoPorcentaje").value(0.0));
    }

    @Test
    void obtenerSocio_MismoUsuarioEsSocioActivo_DevuelveDatosReales() throws Exception {
        // 1. Convertimos al usuario en socio en la BBDD
        Socio s = new Socio();
        s.setUsuario(usuarioPrueba); // Al tener @Transactional, usuarioPrueba sigue "attached"
        s.setNumeroSocio("S-12345");
        s.setEstado("activo");
        s.setTipoCuota("ANUAL");
        s.setFechaAlta(LocalDate.now());
        s.setDescuentoPorcentaje(new BigDecimal("15.00"));
        socioRepository.save(s);

        // 2. Comprobamos que devuelve los datos correctamente
        mockMvc.perform(get("/usuarios/" + usuarioPrueba.getId() + "/socio")
                        .header("X-User-Id", usuarioPrueba.getId().toString())
                        .header("X-User-Role", "socio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("activo"))
                .andExpect(jsonPath("$.numeroSocio").value("S-12345"))
                .andExpect(jsonPath("$.descuentoPorcentaje").value(15.0));
    }

    @Test
    void obtenerSocio_OtroUsuarioSinPermisos_Devuelve403Forbidden() throws Exception {
        UUID idAtacante = UUID.randomUUID();

        mockMvc.perform(get("/usuarios/" + usuarioPrueba.getId() + "/socio")
                        .header("X-User-Id", idAtacante.toString())
                        .header("X-User-Role", "visitante"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tienes permisos para ver el estado de socio de este perfil"));
    }

    @Test
    void obtenerSocio_UsuarioNoExiste_Devuelve404NotFound() throws Exception {
        UUID idInexistente = UUID.randomUUID();

        mockMvc.perform(get("/usuarios/" + idInexistente + "/socio")
                        .header("X-User-Id", idInexistente.toString())
                        .header("X-User-Role", "visitante"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Usuario no encontrado"));
    }

    // ==========================================
    // TESTS PARA: PUT /usuarios/{id}/socio (Actualizar/Dar de alta)
    // ==========================================

    @Test
    void actualizarSocio_ComoAdmin_DaDeAltaYCambiaRol() throws Exception {
        SocioUpdateDTO dto = new SocioUpdateDTO();
        dto.setTipoCuota("MENSUAL");
        dto.setFechaCaducidad(LocalDate.now().plusMonths(1));

        mockMvc.perform(put("/usuarios/" + usuarioPrueba.getId() + "/socio")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("activo"))
                .andExpect(jsonPath("$.tipoCuota").value("MENSUAL"));

        assertEquals(1, socioRepository.count(), "Se debió crear el registro de socio");
        Usuario usuarioActualizado = usuarioRepository.findById(usuarioPrueba.getId()).get();
        assertEquals(Rol.socio, usuarioActualizado.getRol(), "El rol del usuario debió actualizarse");
    }

    @Test
    void actualizarSocio_ComoVisitante_Devuelve403Forbidden() throws Exception {
        SocioUpdateDTO dto = new SocioUpdateDTO();
        dto.setTipoCuota("MENSUAL");
        dto.setFechaCaducidad(LocalDate.now().plusMonths(1));

        mockMvc.perform(put("/usuarios/" + usuarioPrueba.getId() + "/socio")
                        .header("X-User-Role", "visitante")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Solo los administradores pueden gestionar las cuotas de los socios"));

        assertEquals(0, socioRepository.count(), "No se debió crear ningún socio");
    }

    @Test
    void actualizarSocio_UsuarioNoExiste_Devuelve404NotFound() throws Exception {
        SocioUpdateDTO dto = new SocioUpdateDTO();
        dto.setTipoCuota("MENSUAL");
        dto.setFechaCaducidad(LocalDate.now().plusMonths(1));

        UUID idInexistente = UUID.randomUUID();

        mockMvc.perform(put("/usuarios/" + idInexistente + "/socio")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("Usuario no encontrado"));
    }
}