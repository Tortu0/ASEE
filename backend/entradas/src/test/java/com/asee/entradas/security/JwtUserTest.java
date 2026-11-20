package com.asee.entradas.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUserTest {

    private static JwtAuthenticationToken auth(Consumer<Jwt.Builder> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject("u1");
        claims.accept(b);
        return new JwtAuthenticationToken(b.build());
    }

    @Test
    void extraeElUsuarioIdDelSubject() {
        assertThat(JwtUser.from(auth(b -> {})).getUserId()).isEqualTo("u1");
    }

    @Test
    void sinClaimDeRol_esVisitante() {
        JwtUser u = JwtUser.from(auth(b -> {}));

        assertThat(u.getRole()).isEqualTo("visitante");
        assertThat(u.isStaff()).isFalse();
        assertThat(u.isAdmin()).isFalse();
    }

    @Test
    void leeElRolDeLosClaimsRoleRolYRoles() {
        assertThat(JwtUser.from(auth(b -> b.claim("role", "socio"))).getRole()).isEqualTo("socio");
        assertThat(JwtUser.from(auth(b -> b.claim("rol", "socio"))).getRole()).isEqualTo("socio");
        assertThat(JwtUser.from(auth(b -> b.claim("roles", List.of("socio")))).getRole()).isEqualTo("socio");
    }

    @Test
    void normalizaElRol_prefijoROLE_mayusculasYEspacios() {
        JwtUser u = JwtUser.from(auth(b -> b.claim("rol", " ROLE_Staff ")));

        assertThat(u.getRole()).isEqualTo("staff");
        assertThat(u.isStaff()).isTrue();
    }

    @Test
    void staffYAdminSonStaff_peroSoloAdminEsAdmin() {
        JwtUser staff = JwtUser.from(auth(b -> b.claim("rol", "staff")));
        JwtUser admin = JwtUser.from(auth(b -> b.claim("rol", "admin")));

        assertThat(staff.isStaff()).isTrue();
        assertThat(staff.isAdmin()).isFalse();
        assertThat(admin.isStaff()).isTrue();
        assertThat(admin.isAdmin()).isTrue();
    }

    @Test
    void socioYVisitanteNoSonStaff() {
        assertThat(JwtUser.from(auth(b -> b.claim("rol", "socio"))).isStaff()).isFalse();
        assertThat(JwtUser.from(auth(b -> b.claim("rol", "visitante"))).isStaff()).isFalse();
    }

    @Test
    void conVariosRoles_basta_unoStaff() {
        JwtUser u = JwtUser.from(auth(b -> b.claim("roles", List.of("socio", "STAFF"))));

        assertThat(u.isStaff()).isTrue();
        assertThat(u.getRole()).isEqualTo("socio");   // el primero
    }

    @Test
    void getRole_respetaElOrdenDeClaims_roleAntesQueRol() {
        JwtUser u = JwtUser.from(auth(b -> b.claim("role", "staff").claim("rol", "socio")));

        assertThat(u.getRole()).isEqualTo("staff");
    }

    @Test
    void sinAutenticacion_lanzaIllegalState() {
        assertThatThrownBy(() -> JwtUser.from(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void conUnPrincipalQueNoEsJwt_lanzaIllegalState() {
        assertThatThrownBy(() -> JwtUser.from(new UsernamePasswordAuthenticationToken("user", "pw")))
                .isInstanceOf(IllegalStateException.class);
    }
}
