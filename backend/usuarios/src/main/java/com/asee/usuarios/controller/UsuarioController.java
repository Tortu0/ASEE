package com.asee.usuarios.controller;

import com.asee.usuarios.dto.ErrorDTO;
import com.asee.usuarios.dto.UsuarioCreateDTO;
import com.asee.usuarios.dto.UsuarioResponseDTO;
import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    // Inyectamos el servicio (el cerebro) en el controlador (el recepcionista)
    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<?> registrarUsuario(@RequestBody UsuarioCreateDTO dto) {
        try {
            // 1. Pasamos los datos del DTO al Servicio para que aplique la lógica y guarde en base de datos
            Usuario usuarioGuardado = usuarioService.registrarUsuario(
                    dto.getNombre(),
                    dto.getEmail(),
                    dto.getPassword(),
                    dto.getTelefono(),
                    dto.getDireccion()
            );

            // 2. Mapeamos la Entidad resultante al DTO de respuesta (ocultando el password)
            UsuarioResponseDTO response = new UsuarioResponseDTO();
            response.setUsuarioId(usuarioGuardado.getId());
            response.setNombre(usuarioGuardado.getNombre());
            response.setEmail(usuarioGuardado.getEmail());
            response.setTelefono(usuarioGuardado.getTelefono());
            response.setDireccion(usuarioGuardado.getDireccion());
            response.setRol(usuarioGuardado.getRol());
            response.setFechaAlta(usuarioGuardado.getFechaAlta());

            // 3. El YAML exige devolver un 201 Created y una cabecera 'Location' con la URL del nuevo recurso
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(usuarioGuardado.getId())
                    .toUri();

            return ResponseEntity.created(location).body(response);

        } catch (IllegalArgumentException e) {
            // Si el servicio detecta que el email ya existe, lanza esta excepción.
            // La atrapamos y devolvemos el Error 409 Conflict que pide el YAML.
            ErrorDTO error = new ErrorDTO(409, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
    }
}