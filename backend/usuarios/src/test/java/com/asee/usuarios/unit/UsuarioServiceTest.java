package com.asee.usuarios.unit;

import com.asee.usuarios.dto.UsuarioCreateDTO;
import com.asee.usuarios.entity.Rol;
import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.repository.UsuarioRepository;
import com.asee.usuarios.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    // ==========================================
    // TESTS PARA: registrarUsuario (POST)
    // ==========================================

    @Test
    void registrarUsuario_EmailNoExiste_GuardaUsuarioCorrectamente() {
        // A. Preparación (Arrange) - Ahora usamos el DTO limpio
        UsuarioCreateDTO dto = new UsuarioCreateDTO();
        dto.setNombre("Test");
        dto.setEmail("nuevo@correo.com");
        dto.setPassword("Password123!");
        dto.setTelefono("+34600000000");
        dto.setDireccion("Calle Falsa 123");

        when(usuarioRepository.existsByEmail(dto.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(dto.getPassword())).thenReturn("hash_simulado_argon2id");

        Usuario usuarioSimulado = new Usuario();
        usuarioSimulado.setEmail(dto.getEmail());
        usuarioSimulado.setRol(Rol.visitante);
        usuarioSimulado.setPassword("hash_simulado_argon2id");

        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioSimulado);

        // B. Ejecución (Act) - Pasamos el DTO como único argumento
        Usuario resultado = usuarioService.registrarUsuario(dto);

        // C. Verificación (Assert)
        assertNotNull(resultado);
        assertEquals("nuevo@correo.com", resultado.getEmail());
        assertEquals(Rol.visitante, resultado.getRol());
        assertEquals("hash_simulado_argon2id", resultado.getPassword());

        verify(usuarioRepository, times(1)).save(any(Usuario.class));
    }

    @Test
    void registrarUsuario_EmailYaExiste_LanzaExcepcion() {
        // A. Preparación (Arrange)
        UsuarioCreateDTO dto = new UsuarioCreateDTO();
        dto.setEmail("duplicado@correo.com");

        when(usuarioRepository.existsByEmail(dto.getEmail())).thenReturn(true);

        // B & C. Ejecución y Verificación
        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class, () -> {
            usuarioService.registrarUsuario(dto);
        });

        assertEquals("El email ya está registrado", excepcion.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    // ==========================================
    // TESTS PARA: obtenerUsuario (GET)
    // ==========================================

    @Test
    void obtenerUsuario_UsuarioExiste_RetornaUsuario() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        Usuario usuarioFalso = new Usuario();
        usuarioFalso.setId(idTest);
        usuarioFalso.setNombre("Juan");

        // Simulamos que el repositorio encuentra al usuario y lo devuelve envuelto en un Optional
        when(usuarioRepository.findById(idTest)).thenReturn(Optional.of(usuarioFalso));

        // B. Ejecución
        Optional<Usuario> resultado = usuarioService.obtenerUsuario(idTest);

        // C. Verificación
        assertTrue(resultado.isPresent(), "El usuario debería estar presente");
        assertEquals("Juan", resultado.get().getNombre());
        verify(usuarioRepository, times(1)).findById(idTest);
    }

    @Test
    void obtenerUsuario_UsuarioNoExiste_RetornaOptionalVacio() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();

        // Simulamos que la base de datos devuelve un Optional vacío (no encontró el ID)
        when(usuarioRepository.findById(idTest)).thenReturn(Optional.empty());

        // B. Ejecución
        Optional<Usuario> resultado = usuarioService.obtenerUsuario(idTest);

        // C. Verificación
        assertTrue(resultado.isEmpty(), "El resultado debería estar vacío si el usuario no existe");
        verify(usuarioRepository, times(1)).findById(idTest);
    }

    // ==========================================
    // TESTS PARA: actualizarUsuario (PUT)
    // ==========================================

    @Test
    void actualizarUsuario_UsuarioExisteYDatosValidos_ActualizaYGuarda() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();

        Usuario usuarioEnBBDD = new Usuario();
        usuarioEnBBDD.setId(idTest);
        usuarioEnBBDD.setNombre("Nombre Viejo");
        usuarioEnBBDD.setTelefono("111");

        com.asee.usuarios.dto.UsuarioUpdateDTO dto = new com.asee.usuarios.dto.UsuarioUpdateDTO();
        dto.setNombre("Nombre Nuevo");
        dto.setTelefono("999");
        // No enviamos dirección para simular una actualización parcial

        when(usuarioRepository.findById(idTest)).thenReturn(Optional.of(usuarioEnBBDD));
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEnBBDD);

        // B. Ejecución
        Usuario resultado = usuarioService.actualizarUsuario(idTest, dto);

        // C. Verificación
        assertEquals("Nombre Nuevo", resultado.getNombre(), "El nombre debería haberse actualizado");
        assertEquals("999", resultado.getTelefono(), "El teléfono debería haberse actualizado");
        verify(usuarioRepository, times(1)).save(usuarioEnBBDD);
    }

    @Test
    void actualizarUsuario_UsuarioNoExiste_LanzaExcepcion() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        com.asee.usuarios.dto.UsuarioUpdateDTO dto = new com.asee.usuarios.dto.UsuarioUpdateDTO();
        dto.setNombre("Nuevo");

        when(usuarioRepository.findById(idTest)).thenReturn(Optional.empty());

        // B & C. Ejecución y Verificación
        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class, () -> {
            usuarioService.actualizarUsuario(idTest, dto);
        });

        assertEquals("Usuario no encontrado", excepcion.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    // ==========================================
    // TESTS PARA: eliminarUsuario (DELETE)
    // ==========================================

    @Test
    void eliminarUsuario_UsuarioExiste_EliminaCorrectamente() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        when(usuarioRepository.existsById(idTest)).thenReturn(true);

        // B. Ejecución
        usuarioService.eliminarUsuario(idTest);

        // C. Verificación
        verify(usuarioRepository, times(1)).deleteById(idTest);
    }

    @Test
    void eliminarUsuario_UsuarioNoExiste_LanzaExcepcion() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        when(usuarioRepository.existsById(idTest)).thenReturn(false);

        // B & C. Ejecución y Verificación
        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class, () -> {
            usuarioService.eliminarUsuario(idTest);
        });

        assertEquals("Usuario no encontrado", excepcion.getMessage());
        verify(usuarioRepository, never()).deleteById(any());
    }

    // ==========================================
    // TESTS PARA: cambiarRol (PUT)
    // ==========================================

    @Test
    void cambiarRol_UsuarioExiste_ActualizaYGuarda() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        Usuario usuarioEnBBDD = new Usuario();
        usuarioEnBBDD.setId(idTest);
        usuarioEnBBDD.setRol(Rol.visitante); // Rol inicial

        when(usuarioRepository.findById(idTest)).thenReturn(Optional.of(usuarioEnBBDD));
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(usuarioEnBBDD);

        // B. Ejecución
        Usuario resultado = usuarioService.cambiarRol(idTest, Rol.admin);

        // C. Verificación
        assertEquals(Rol.admin, resultado.getRol(), "El rol debería haberse actualizado a admin");
        verify(usuarioRepository, times(1)).save(usuarioEnBBDD);
    }

    @Test
    void cambiarRol_UsuarioNoExiste_LanzaExcepcion() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        when(usuarioRepository.findById(idTest)).thenReturn(Optional.empty());

        // B & C. Ejecución y Verificación
        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class, () -> {
            usuarioService.cambiarRol(idTest, Rol.admin);
        });

        assertEquals("Usuario no encontrado", excepcion.getMessage());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }
}