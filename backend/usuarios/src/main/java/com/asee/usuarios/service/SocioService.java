package com.asee.usuarios.service;

import com.asee.usuarios.dto.SocioUpdateDTO;
import com.asee.usuarios.entity.Rol;
import com.asee.usuarios.entity.Socio;
import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.repository.SocioRepository;
import com.asee.usuarios.repository.UsuarioRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class SocioService {

    private final SocioRepository socioRepository;
    private final UsuarioRepository usuarioRepository;

    public SocioService(SocioRepository socioRepository, UsuarioRepository usuarioRepository) {
        this.socioRepository = socioRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // --- EL CRON JOB AUTOMATIZADO ---
    // Se ejecuta a las 00:00:00 todos los días
    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void revisarCaducidadesDiarias() {
        int filasActualizadas = socioRepository.expirarCuotasCaducadas();
        System.out.println("[SISTEMA] Mantenimiento diario: " + filasActualizadas + " socios han caducado hoy.");
    }

    // 1. OBTENER ESTADO DE SOCIO (Ahora mucho más limpio)
    public Socio obtenerEstadoSocio(UUID usuarioId) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }

        Optional<Socio> socioOpt = socioRepository.findById(usuarioId);

        // Si existe, lo devolvemos tal cual (la base de datos ya está siempre actualizada por el Cron Job)
        if (socioOpt.isPresent()) {
            return socioOpt.get();
        }

        // Si nunca fue socio, construimos la respuesta por defecto
        Socio noSocio = new Socio();
        noSocio.setUsuarioId(usuarioId);
        noSocio.setEstado("no_socio");
        noSocio.setDescuentoPorcentaje(BigDecimal.ZERO);
        return noSocio;
    }

    // 2. DAR DE ALTA O RENOVAR SOCIO (Se mantiene igual que antes)
    @Transactional
    public Socio actualizarSocio(UUID usuarioId, SocioUpdateDTO dto) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Socio socio = socioRepository.findById(usuarioId).orElse(new Socio());

        if (socio.getUsuario() == null) {
            socio.setUsuario(usuario);
            socio.setFechaAlta(LocalDate.now());
            socio.setNumeroSocio("S-" + usuarioId.toString().substring(0, 6).toUpperCase());
        }

        socio.setTipoCuota(dto.getTipoCuota());
        socio.setFechaCaducidad(dto.getFechaCaducidad());
        socio.setEstado("activo");
        socio.setDescuentoPorcentaje(new BigDecimal("15.00"));

        if (usuario.getRol() == Rol.visitante) {
            usuario.setRol(Rol.socio);
            usuarioRepository.save(usuario);
        }

        return socioRepository.save(socio);
    }
}