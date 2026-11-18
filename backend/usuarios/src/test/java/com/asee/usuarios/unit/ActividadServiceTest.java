package com.asee.usuarios.unit;

import com.asee.usuarios.dto.ActividadResponseDTO;
import com.asee.usuarios.entity.Actividad;
import com.asee.usuarios.repository.ActividadRepository;
import com.asee.usuarios.repository.UsuarioRepository;
import com.asee.usuarios.service.ActividadService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ActividadServiceTest {

    @Mock
    private ActividadRepository actividadRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ActividadService actividadService;

    // ==========================================
    // TESTS PARA: obtenerHistorialUsuario (GET)
    // ==========================================

    @Test
    void obtenerHistorialUsuario_UsuarioNoExiste_LanzaExcepcion() {
        // A. Preparación (Arrange)
        UUID idTest = UUID.randomUUID();
        when(usuarioRepository.existsById(idTest)).thenReturn(false);

        // B & C. Ejecución y Verificación combinadas
        IllegalArgumentException excepcion = assertThrows(IllegalArgumentException.class, () -> {
            actividadService.obtenerHistorialUsuario(idTest);
        });

        assertEquals("Usuario no encontrado", excepcion.getMessage());
        verify(actividadRepository, never()).findByUsuarioIdOrderByFechaDesc(any());
    }

    @Test
    void obtenerHistorialUsuario_UsuarioExiste_RetornaListaOrdenada() {
        // A. Preparación (Arrange)
        UUID idTest = UUID.randomUUID();
        when(usuarioRepository.existsById(idTest)).thenReturn(true);

        // Creamos dos actividades simuladas
        Actividad act1 = new Actividad();
        act1.setTipo("COMPRA_ENTRADA");
        act1.setReferenciaId("TICKET-001");
        act1.setFecha(LocalDateTime.now());

        Actividad act2 = new Actividad();
        act2.setTipo("RESERVA_PISTA");
        act2.setReferenciaId("RES-999");
        act2.setFecha(LocalDateTime.now().minusDays(1)); // Más antigua

        // Simulamos que la base de datos las devuelve ordenadas
        List<Actividad> historialSimulado = Arrays.asList(act1, act2);
        when(actividadRepository.findByUsuarioIdOrderByFechaDesc(idTest)).thenReturn(historialSimulado);

        // B. Ejecución (Act)
        List<ActividadResponseDTO> resultado = actividadService.obtenerHistorialUsuario(idTest);

        // C. Verificación (Assert)
        assertNotNull(resultado);
        assertEquals(2, resultado.size(), "El historial debería contener 2 actividades");

        // Verificamos que el mapeo a DTO se hizo correctamente para el primer elemento
        assertEquals("COMPRA_ENTRADA", resultado.get(0).getTipo());
        assertEquals("TICKET-001", resultado.get(0).getReferenciaId());

        // Verificamos que el repositorio fue llamado exactamente una vez
        verify(actividadRepository, times(1)).findByUsuarioIdOrderByFechaDesc(idTest);
    }
}