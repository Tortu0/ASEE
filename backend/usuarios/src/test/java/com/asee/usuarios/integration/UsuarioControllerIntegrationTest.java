package com.asee.usuarios.integration;

import com.asee.usuarios.dto.UsuarioCreateDTO;
import com.asee.usuarios.dto.UsuarioUpdateDTO;
import com.asee.usuarios.entity.Rol;
import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Enciende el simulador de peticiones HTTP
@AutoConfigureMockMvc
public class UsuarioControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
    }

    // =======================================================
    // MÉTODO AUXILIAR PARA CREAR USUARIOS EN LA BBDD DE DOCKER
    // =======================================================
    private Usuario crearUsuarioEnBBDD(String email, Rol rol) {
        Usuario u = new Usuario();
        u.setNombre("Usuario Test");
        u.setEmail(email);
        u.setPassword("password_encriptada");
        u.setTelefono("123456789");
        u.setRol(rol);
        return usuarioRepository.save(u);
    }

    // ==========================================
    // TESTS PARA: POST /usuarios (Registro)
    // ==========================================

    @Test
    void registrarUsuario_DatosValidos_Devuelve201YGuardaEnBBDD() throws Exception {
        UsuarioCreateDTO dto = new UsuarioCreateDTO();
        dto.setNombre("Usuario Integracion");
        dto.setEmail("integration@correo.com");
        dto.setPassword("Password123!");

        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated()) // 201 Created
                .andExpect(jsonPath("$.email").value("integration@correo.com"))
                .andExpect(jsonPath("$.rol").value("visitante"));

        assertEquals(1, usuarioRepository.count());
    }

    @Test
    void registrarUsuario_EmailDuplicado_Devuelve409Conflict() throws Exception {
        // 1. Guardamos un usuario previamente
        crearUsuarioEnBBDD("duplicado@correo.com", Rol.visitante);

        // 2. Intentamos registrar otro con el mismo email
        UsuarioCreateDTO dto = new UsuarioCreateDTO();
        dto.setNombre("Otro");
        dto.setEmail("duplicado@correo.com");
        dto.setPassword("1234");

        // 3. Verificamos que el Controlador atrapa la excepción y devuelve 409
        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isConflict()); // 409 Conflict según el YAML
    }

    // ==========================================
    // TESTS PARA: GET /usuarios (listado, solo admin) y validación del registro
    // ==========================================

    @Test
    void listarUsuarios_ComoAdmin_Devuelve200YLista() throws Exception {
        crearUsuarioEnBBDD("uno@correo.com", Rol.visitante);
        crearUsuarioEnBBDD("dos@correo.com", Rol.staff);

        mockMvc.perform(get("/usuarios").header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/usuarios").param("rol", "staff").header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rol").value("staff"));
    }

    @Test
    void listarUsuarios_ComoStaff_Devuelve403() throws Exception {
        mockMvc.perform(get("/usuarios").header("X-User-Role", "staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrarUsuario_SinEmail_Devuelve400() throws Exception {
        mockMvc.perform(post("/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sin Email\",\"password\":\"Password123!\"}"))
                .andExpect(status().isBadRequest());
    }

    // ==========================================
    // TESTS PARA: GET /usuarios/{id} (Perfil)
    // ==========================================

    @Test
    void obtenerUsuario_MismoUsuario_Devuelve200OK() throws Exception {
        Usuario guardado = crearUsuarioEnBBDD("test@correo.com", Rol.visitante);

        mockMvc.perform(get("/usuarios/" + guardado.getId())
                        .header("X-User-Id", guardado.getId().toString()) // Petición hecha por él mismo
                        .header("X-User-Role", "visitante"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@correo.com"));
    }

    @Test
    void obtenerUsuario_OtroUsuarioSinPermisos_Devuelve403Forbidden() throws Exception {
        Usuario guardado = crearUsuarioEnBBDD("test@correo.com", Rol.visitante);
        UUID idDeOtroUsuario = UUID.randomUUID(); // Simulamos a un atacante o usuario curioso

        mockMvc.perform(get("/usuarios/" + guardado.getId())
                        .header("X-User-Id", idDeOtroUsuario.toString())
                        .header("X-User-Role", "visitante")) // Rol básico
                .andExpect(status().isForbidden()) // 403 Forbidden
                .andExpect(jsonPath("$.mensaje").value("No tienes permisos para ver el perfil de otro usuario"));
    }

    // ==========================================
    // TESTS PARA: PUT /usuarios/{id} (Actualizar)
    // ==========================================

    @Test
    void actualizarUsuario_AdminActualizaCualquierPerfil_Devuelve200OK() throws Exception {
        Usuario guardado = crearUsuarioEnBBDD("test@correo.com", Rol.visitante);
        UUID idAdmin = UUID.randomUUID(); // Un admin haciendo la petición

        UsuarioUpdateDTO dto = new UsuarioUpdateDTO();
        dto.setNombre("Nombre Modificado");

        mockMvc.perform(put("/usuarios/" + guardado.getId())
                        .header("X-User-Id", idAdmin.toString())
                        .header("X-User-Role", "admin") // Es administrador, tiene permiso total
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre Modificado"));
    }

    // ==========================================
    // TESTS PARA: DELETE /usuarios/{id} (Borrar)
    // ==========================================

    @Test
    void eliminarUsuario_ComoAdmin_Devuelve204NoContent() throws Exception {
        Usuario guardado = crearUsuarioEnBBDD("borrar@correo.com", Rol.visitante);

        mockMvc.perform(delete("/usuarios/" + guardado.getId())
                        .header("X-User-Role", "admin")) // Solo admin puede borrar
                .andExpect(status().isNoContent()); // 204 No Content según el YAML

        // Verificamos que realmente desapareció de la base de datos PostgreSQL
        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void eliminarUsuario_ComoVisitante_Devuelve403Forbidden() throws Exception {
        Usuario guardado = crearUsuarioEnBBDD("borrar@correo.com", Rol.visitante);

        mockMvc.perform(delete("/usuarios/" + guardado.getId())
                        .header("X-User-Role", "visitante")) // Rol no autorizado
                .andExpect(status().isForbidden()); // 403 Forbidden

        // Verificamos que el escudo funcionó y el usuario SIGUE en la base de datos
        assertEquals(1, usuarioRepository.count());
    }
}