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
            // 1. Pasamos el DTO completo al Servicio directamente
            Usuario usuarioGuardado = usuarioService.registrarUsuario(dto);

            // 2. Mapeamos la Entidad resultante al DTO de respuesta
            UsuarioResponseDTO response = new UsuarioResponseDTO();
            response.setUsuarioId(usuarioGuardado.getId());
            response.setNombre(usuarioGuardado.getNombre());
            response.setEmail(usuarioGuardado.getEmail());
            response.setTelefono(usuarioGuardado.getTelefono());
            response.setDireccion(usuarioGuardado.getDireccion());
            response.setRol(usuarioGuardado.getRol().name());
            response.setFechaAlta(usuarioGuardado.getFechaAlta());

            // 3. El YAML exige devolver un 201 Created y una cabecera 'Location'
            java.net.URI location = org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}")
                    .buildAndExpand(usuarioGuardado.getId())
                    .toUri();

            return ResponseEntity.created(location).body(response);

        } catch (IllegalArgumentException e) {
            ErrorDTO error = new ErrorDTO(409, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }
    }

    @GetMapping("/{usuarioId}")
    public ResponseEntity<?> obtenerUsuario(
            @PathVariable java.util.UUID usuarioId,
            @RequestHeader("X-User-Id") java.util.UUID headerUserId,
            @RequestHeader("X-User-Role") String headerUserRole) {

        // 1. REGLA DE SEGURIDAD (Autorización)
        // El YAML dice: "Un usuario solo puede ver su propio perfil; staff y admin pueden ver cualquiera."
        boolean esAdminOStaff = headerUserRole.equals("admin") || headerUserRole.equals("staff");
        boolean esMismoUsuario = headerUserId.equals(usuarioId);

        if (!esAdminOStaff && !esMismoUsuario) {
            ErrorDTO error = new ErrorDTO(403, "No tienes permisos para ver el perfil de otro usuario");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        // 2. BUSCAR EL RECURSO EN BASE DE DATOS
        java.util.Optional<Usuario> usuarioOpt = usuarioService.obtenerUsuario(usuarioId);

        if (usuarioOpt.isEmpty()) {
            ErrorDTO error = new ErrorDTO(404, "No existe ningún usuario con el identificador indicado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        Usuario usuario = usuarioOpt.get();

        // 3. MAPEAR AL DTO DE RESPUESTA (Ocultar contraseña)
        UsuarioResponseDTO response = new UsuarioResponseDTO();
        response.setUsuarioId(usuario.getId());
        response.setNombre(usuario.getNombre());
        response.setEmail(usuario.getEmail());
        response.setTelefono(usuario.getTelefono());
        response.setDireccion(usuario.getDireccion());
        response.setRol(usuario.getRol().name());
        response.setFechaAlta(usuario.getFechaAlta());

        // 4. DEVOLVER EL PERFIL CON CÓDIGO 200 OK
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{usuarioId}")
    public ResponseEntity<?> actualizarUsuario(
            @PathVariable java.util.UUID usuarioId,
            @RequestBody com.asee.usuarios.dto.UsuarioUpdateDTO dto,
            @RequestHeader("X-User-Id") java.util.UUID headerUserId,
            @RequestHeader("X-User-Role") String headerUserRole) {

        // 1. REGLA DE SEGURIDAD: Solo el propio usuario o admin
        boolean esAdmin = headerUserRole.equals("admin");
        boolean esMismoUsuario = headerUserId.equals(usuarioId);

        if (!esAdmin && !esMismoUsuario) {
            ErrorDTO error = new ErrorDTO(403, "No tienes permisos para modificar este perfil");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        // 2. REGLA DE NEGOCIO: Rechazar cuerpo vacío
        if (dto.isVacio()) {
            ErrorDTO error = new ErrorDTO(400, "El cuerpo de la petición no puede estar vacío");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        try {
            // 3. ACTUALIZAR Y GUARDAR
            Usuario usuarioActualizado = usuarioService.actualizarUsuario(usuarioId, dto);

            // 4. MAPEAR AL DTO DE SALIDA
            UsuarioResponseDTO response = new UsuarioResponseDTO();
            response.setUsuarioId(usuarioActualizado.getId());
            response.setNombre(usuarioActualizado.getNombre());
            response.setEmail(usuarioActualizado.getEmail());
            response.setTelefono(usuarioActualizado.getTelefono());
            response.setDireccion(usuarioActualizado.getDireccion());
            response.setRol(usuarioActualizado.getRol().name());
            response.setFechaAlta(usuarioActualizado.getFechaAlta());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorDTO(404, e.getMessage()));
        }
    }

    @DeleteMapping("/{usuarioId}")
    public ResponseEntity<?> eliminarUsuario(
            @PathVariable java.util.UUID usuarioId,
            @RequestHeader("X-User-Role") String headerUserRole) {

        // REGLA: Solo admin puede borrar
        if (!headerUserRole.equals("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorDTO(403, "Solo los administradores pueden eliminar usuarios"));
        }

        try {
            usuarioService.eliminarUsuario(usuarioId);
            // El YAML exige un 204 No Content para borrados exitosos (sin cuerpo de respuesta)
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorDTO(404, e.getMessage()));
        }
    }

    @PutMapping("/{usuarioId}/rol")
    public ResponseEntity<?> cambiarRol(
            @PathVariable java.util.UUID usuarioId,
            @RequestBody com.asee.usuarios.dto.RolUpdateDTO dto,
            @RequestHeader("X-User-Role") String headerUserRole) {

        // REGLA: Solo admin puede cambiar roles
        if (!headerUserRole.equals("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorDTO(403, "Solo los administradores pueden cambiar roles"));
        }

        if (dto.getRol() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorDTO(400, "El campo 'rol' es obligatorio"));
        }

        try {
            Usuario usuarioActualizado = usuarioService.cambiarRol(usuarioId, dto.getRol());

            UsuarioResponseDTO response = new UsuarioResponseDTO();
            response.setUsuarioId(usuarioActualizado.getId());
            response.setNombre(usuarioActualizado.getNombre());
            response.setEmail(usuarioActualizado.getEmail());
            response.setTelefono(usuarioActualizado.getTelefono());
            response.setDireccion(usuarioActualizado.getDireccion());
            response.setRol(usuarioActualizado.getRol().name());
            response.setFechaAlta(usuarioActualizado.getFechaAlta());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorDTO(404, e.getMessage()));
        }
    }
}