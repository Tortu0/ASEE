package com.asee.usuarios.config;

import com.asee.usuarios.entity.Rol;
import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el administrador inicial. El registro público siempre crea "visitante" y solo un admin puede
 * cambiar roles, así que sin esto no habría forma de obtener el primer administrador.
 * Se activa con las variables ADMIN_EMAIL y ADMIN_PASSWORD (y opcionalmente ADMIN_NOMBRE).
 * Si el email ya existe, se le asigna el rol admin sin tocar su contraseña.
 */
@Component
public class AdminInicialRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicialRunner.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String nombre;

    public AdminInicialRunner(UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder,
                              @Value("${usuarios.admin-inicial.email:}") String email,
                              @Value("${usuarios.admin-inicial.password:}") String password,
                              @Value("${usuarios.admin-inicial.nombre:Administrador}") String nombre) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.nombre = nombre;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;   // no configurado: no se hace nada
        }
        Usuario existente = usuarioRepository.findByEmail(email).orElse(null);
        if (existente == null) {
            Usuario admin = new Usuario();
            admin.setNombre(nombre);
            admin.setEmail(email);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRol(Rol.admin);
            usuarioRepository.save(admin);
            log.info("Administrador inicial creado: {}", email);
        } else if (existente.getRol() != Rol.admin) {
            existente.setRol(Rol.admin);
            usuarioRepository.save(existente);
            log.info("Usuario {} promocionado a administrador", email);
        }
    }
}
