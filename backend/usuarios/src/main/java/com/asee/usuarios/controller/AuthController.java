package com.asee.usuarios.controller;

import com.asee.usuarios.config.JwtProvider;
import com.asee.usuarios.dto.ErrorDTO;
import com.asee.usuarios.dto.LoginRequestDTO;
import com.asee.usuarios.dto.LoginResponseDTO;
import com.asee.usuarios.dto.UsuarioResponseDTO;
import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.service.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    // Inyectamos las tres herramientas necesarias para el proceso de login
    public AuthController(UsuarioService usuarioService, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO dto) {
        // 1. Buscar al usuario por su email en la base de datos
        Optional<Usuario> usuarioOpt = usuarioService.buscarPorEmail(dto.getEmail());

        // Si el email no existe, devolvemos 401 Unauthorized (evitamos dar pistas de si falla el email o la clave)
        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorDTO(401, "Email o contraseña incorrectos"));
        }

        Usuario usuario = usuarioOpt.get();

        // 2. Verificar la contraseña usando Argon2id
        // IMPORTANTE: Nunca uses .equals() para comparar contraseñas encriptadas.
        // Argon2id genera un "salt" (ruido aleatorio) diferente cada vez.
        // Solo passwordEncoder.matches() sabe hacer las matemáticas para comprobar si coinciden.
        if (!passwordEncoder.matches(dto.getPassword(), usuario.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorDTO(401, "Email o contraseña incorrectos"));
        }

        // 3. Credenciales correctas: Fabricar el Token JWT
        String token = jwtProvider.generarToken(usuario);

        // 4. Mapear los datos del usuario al DTO de salida (sin contraseña)
        UsuarioResponseDTO usuarioResponse = new UsuarioResponseDTO();
        usuarioResponse.setUsuarioId(usuario.getId());
        usuarioResponse.setNombre(usuario.getNombre());
        usuarioResponse.setEmail(usuario.getEmail());
        usuarioResponse.setTelefono(usuario.getTelefono());
        usuarioResponse.setDireccion(usuario.getDireccion());
        usuarioResponse.setRol(usuario.getRol());
        usuarioResponse.setFechaAlta(usuario.getFechaAlta());

        // 5. Construir la respuesta final tal y como exige el YAML
        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setExpiraEn(3600); // 1 hora, como pusimos en el application.yml
        response.setUsuario(usuarioResponse);

        return ResponseEntity.ok(response);
    }
}