package com.asee.deportivo.controller;

import com.asee.deportivo.dto.PreferenciasDto;
import com.asee.deportivo.exception.AccesoDenegadoException;
import com.asee.deportivo.security.UsuarioAutenticado;
import com.asee.deportivo.service.PreferenciasService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/usuarios/{usuarioId}/preferencias-notificacion")
public class PreferenciasController {

    private final PreferenciasService service;

    public PreferenciasController(PreferenciasService service) {
        this.service = service;
    }

    @GetMapping
    public PreferenciasDto obtener(@PathVariable("usuarioId") UUID usuarioId,
                                   Authentication auth) {
        comprobarAcceso(usuarioId, auth);
        return PreferenciasDto.from(service.obtener(usuarioId));
    }

    @PutMapping
    public PreferenciasDto actualizar(@PathVariable("usuarioId") UUID usuarioId,
                                      @RequestBody PreferenciasDto dto,
                                      Authentication auth) {
        comprobarAcceso(usuarioId, auth);
        return PreferenciasDto.from(service.actualizar(usuarioId, dto));
    }

    // Solo el propio usuario, o staff/admin (según el YAML)
    private void comprobarAcceso(UUID usuarioId, Authentication auth) {
        if (!UsuarioAutenticado.esPropietario(auth, usuarioId)
                && !UsuarioAutenticado.esStaffOAdmin(auth)) {
            throw new AccesoDenegadoException(
                    "Solo el propio usuario, staff o admin pueden acceder a estas preferencias");
        }
    }
}