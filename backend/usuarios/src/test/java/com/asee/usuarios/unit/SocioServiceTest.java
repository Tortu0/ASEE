package com.asee.usuarios.unit;

import com.asee.usuarios.dto.SocioUpdateDTO;
import com.asee.usuarios.entity.Rol;
import com.asee.usuarios.entity.Socio;
import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.repository.SocioRepository;
import com.asee.usuarios.repository.UsuarioRepository;
import com.asee.usuarios.service.SocioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SocioServiceTest {

    @Mock
    private SocioRepository socioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private SocioService socioService;

    // ==========================================
    // TESTS PARA: revisarCaducidadesDiarias (CRON JOB)
    // ==========================================

    @Test
    void revisarCaducidadesDiarias_EjecutaConsulta_ImprimeResultado() {
        // A. Preparación (Arrange)
        when(socioRepository.expirarCuotasCaducadas()).thenReturn(5);

        // B. Ejecución (Act)
        assertDoesNotThrow(() -> socioService.revisarCaducidadesDiarias());

        // C. Verificación (Assert)
        verify(socioRepository, times(1)).expirarCuotasCaducadas();
    }

    // ==========================================
    // TESTS PARA: obtenerEstadoSocio (GET)
    // ==========================================

    @Test
    void obtenerEstadoSocio_UsuarioNoExiste_LanzaExcepcion() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        when(usuarioRepository.existsById(idTest)).thenReturn(false);

        // B & C. Ejecución y Verificación
        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class, () -> {
            socioService.obtenerEstadoSocio(idTest);
        });

        assertEquals("Usuario no encontrado", excepcion.getMessage());
        verify(socioRepository, never()).findById(idTest);
    }

    @Test
    void obtenerEstadoSocio_UsuarioEsSocio_RetornaSocioExistente() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        Socio socioEnBBDD = new Socio();
        socioEnBBDD.setUsuarioId(idTest);
        socioEnBBDD.setEstado("activo");

        when(usuarioRepository.existsById(idTest)).thenReturn(true);
        when(socioRepository.findById(idTest)).thenReturn(Optional.of(socioEnBBDD));

        // B. Ejecución
        Socio resultado = socioService.obtenerEstadoSocio(idTest);

        // C. Verificación
        assertNotNull(resultado);
        assertEquals("activo", resultado.getEstado());
        assertEquals(idTest, resultado.getUsuarioId());
    }

    @Test
    void obtenerEstadoSocio_UsuarioNoEsSocio_RetornaSocioPorDefecto() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();

        when(usuarioRepository.existsById(idTest)).thenReturn(true);
        when(socioRepository.findById(idTest)).thenReturn(Optional.empty());

        // B. Ejecución
        Socio resultado = socioService.obtenerEstadoSocio(idTest);

        // C. Verificación
        assertNotNull(resultado);
        assertEquals("no_socio", resultado.getEstado());
        assertEquals(BigDecimal.ZERO, resultado.getDescuentoPorcentaje());
        assertEquals(idTest, resultado.getUsuarioId());
    }

    // ==========================================
    // TESTS PARA: actualizarSocio (PUT)
    // ==========================================

    @Test
    void actualizarSocio_UsuarioNoExiste_LanzaExcepcion() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        SocioUpdateDTO dto = new SocioUpdateDTO();

        when(usuarioRepository.findById(idTest)).thenReturn(Optional.empty());

        // B & C. Ejecución y Verificación
        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class, () -> {
            socioService.actualizarSocio(idTest, dto);
        });

        assertEquals("Usuario no encontrado", excepcion.getMessage());
        verify(socioRepository, never()).save(any(Socio.class));
    }

    @Test
    void actualizarSocio_UsuarioNuevoSocio_CreaSocioYActualizaRol() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        Usuario usuarioVisitante = new Usuario();
        usuarioVisitante.setId(idTest);
        usuarioVisitante.setRol(Rol.visitante);

        SocioUpdateDTO dto = new SocioUpdateDTO();
        dto.setTipoCuota("MENSUAL");
        dto.setFechaCaducidad(LocalDate.now().plusMonths(1));

        when(usuarioRepository.findById(idTest)).thenReturn(Optional.of(usuarioVisitante));
        when(socioRepository.findById(idTest)).thenReturn(Optional.empty()); // No es socio aún

        Socio socioGuardado = new Socio();
        socioGuardado.setUsuarioId(idTest);
        socioGuardado.setEstado("activo");
        socioGuardado.setTipoCuota("MENSUAL");

        when(socioRepository.save(any(Socio.class))).thenReturn(socioGuardado);

        // B. Ejecución
        Socio resultado = socioService.actualizarSocio(idTest, dto);

        // C. Verificación
        assertNotNull(resultado);
        assertEquals("activo", resultado.getEstado());

        // Verifica que se cambió el rol del usuario a socio
        assertEquals(Rol.socio, usuarioVisitante.getRol());
        verify(usuarioRepository, times(1)).save(usuarioVisitante);
        verify(socioRepository, times(1)).save(any(Socio.class));
    }

    @Test
    void actualizarSocio_UsuarioYaEsSocio_ActualizaCuotaSinCambiarRol() {
        // A. Preparación
        UUID idTest = UUID.randomUUID();
        Usuario usuarioSocio = new Usuario();
        usuarioSocio.setId(idTest);
        usuarioSocio.setRol(Rol.socio); // Ya es socio

        Socio socioExistente = new Socio();
        socioExistente.setUsuario(usuarioSocio);
        socioExistente.setNumeroSocio("S-123456");

        SocioUpdateDTO dto = new SocioUpdateDTO();
        dto.setTipoCuota("ANUAL");
        dto.setFechaCaducidad(LocalDate.now().plusYears(1));

        when(usuarioRepository.findById(idTest)).thenReturn(Optional.of(usuarioSocio));
        when(socioRepository.findById(idTest)).thenReturn(Optional.of(socioExistente));
        when(socioRepository.save(any(Socio.class))).thenReturn(socioExistente);

        // B. Ejecución
        socioService.actualizarSocio(idTest, dto);

        // C. Verificación
        // Verifica que NO se volvió a guardar el usuario porque su rol ya era correcto
        verify(usuarioRepository, never()).save(usuarioSocio);
        verify(socioRepository, times(1)).save(socioExistente);
    }
}