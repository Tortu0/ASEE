package com.asee.entradas.service;

import com.asee.entradas.client.DeportivoClient;
import com.asee.entradas.client.UsuariosClient;
import com.asee.entradas.config.EntradasProperties;
import com.asee.entradas.dto.AforoDto;
import com.asee.entradas.dto.AsientoDto;
import com.asee.entradas.dto.CompraEntradas;
import com.asee.entradas.dto.EntradaDto;
import com.asee.entradas.dto.PrecioDto;
import com.asee.entradas.dto.ReservaCreate;
import com.asee.entradas.dto.ReservaDto;
import com.asee.entradas.entity.Asiento;
import com.asee.entradas.entity.Entrada;
import com.asee.entradas.entity.EstadoAsiento;
import com.asee.entradas.entity.EstadoEntrada;
import com.asee.entradas.entity.EstadoReserva;
import com.asee.entradas.entity.MetodoPago;
import com.asee.entradas.entity.Reserva;
import com.asee.entradas.entity.Tarifa;
import com.asee.entradas.entity.TipoEntrada;
import com.asee.entradas.exception.ApiException;
import com.asee.entradas.messaging.EntradaEventPublisher;
import com.asee.entradas.repository.AsientoRepository;
import com.asee.entradas.repository.EntradaRepository;
import com.asee.entradas.repository.ReservaRepository;
import com.asee.entradas.repository.TarifaRepository;
import com.mongodb.client.result.UpdateResult;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link EntradasService}: toda la lógica de negocio, con los repositorios,
 * los clientes HTTP, el publicador de eventos y MongoTemplate simulados.
 *
 * Como las transiciones de estado se hacen con operaciones atómicas de MongoTemplate
 * (updateFirst / findAndModify con el estado esperado en la condición), aquí se comprueba
 * tanto el resultado como la condición de las consultas que se envían.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EntradasServiceTest {

    private static final String PARTIDO = "partido-1";
    private static final String USUARIO = "usuario-1";
    private static final String OTRO_USUARIO = "usuario-2";
    private static final Instant FUTURO = Instant.now().plus(1, ChronoUnit.HOURS);

    private static final CompraEntradas COMPRA =
            new CompraEntradas("r1", TipoEntrada.general, MetodoPago.tarjeta);

    @Mock AsientoRepository asientoRepo;
    @Mock ReservaRepository reservaRepo;
    @Mock EntradaRepository entradaRepo;
    @Mock TarifaRepository tarifaRepo;
    @Mock DeportivoClient deportivoClient;
    @Mock UsuariosClient usuariosClient;
    @Mock EntradaEventPublisher eventPublisher;
    @Mock MongoTemplate mongoTemplate;

    private EntradasService service;

    @BeforeEach
    void setUp() {
        EntradasProperties props = new EntradasProperties(
                10, new EntradasProperties.RabbitProps("club.exchange", "entrada.comprada"), List.of());
        service = new EntradasService(asientoRepo, reservaRepo, entradaRepo, tarifaRepo, props,
                deportivoClient, usuariosClient, eventPublisher, mongoTemplate);
    }

    // ─────────────────────────────────────────────
    // Utilidades
    // ─────────────────────────────────────────────

    private static Asiento asiento(String id, String zona, int fila, int numero, EstadoAsiento estado) {
        Asiento a = new Asiento();
        a.setAsientoId(id);
        a.setPartidoId(PARTIDO);
        a.setZona(zona);
        a.setFila(fila);
        a.setNumero(numero);
        a.setEstado(estado);
        return a;
    }

    private static Reserva reserva(String id, String usuario, EstadoReserva estado, Instant expira, String... asientos) {
        Reserva r = new Reserva();
        r.setReservaId(id);
        r.setPartidoId(PARTIDO);
        r.setUsuarioId(usuario);
        r.setAsientoIds(List.of(asientos));
        r.setEstado(estado);
        r.setExpiraEn(expira);
        return r;
    }

    private static Reserva reservaActiva() {
        return reserva("r1", USUARIO, EstadoReserva.activa, FUTURO, "a1", "a2");
    }

    /** La reserva tal y como queda en base de datos tras "reclamarla" (activa → pagando). */
    private static Reserva reservaPagando(double descuento, Instant desde) {
        Reserva r = reserva("r1", USUARIO, EstadoReserva.pagando, FUTURO, "a1", "a2");
        r.setTipoEntrada(TipoEntrada.general);
        r.setMetodoPago(MetodoPago.tarjeta);
        r.setDescuento(descuento);
        r.setPagandoDesde(desde);
        return r;
    }

    private static Entrada entrada(String id, String asientoId, EstadoEntrada estado) {
        Entrada e = new Entrada();
        e.setEntradaId(id);
        e.setPartidoId(PARTIDO);
        e.setUsuarioId(USUARIO);
        e.setAsientoId(asientoId);
        e.setTipoEntrada(TipoEntrada.general);
        e.setPrecioPagado(8.5);
        e.setDescuentoAplicado(0);
        e.setMetodoPago(MetodoPago.tarjeta);
        e.setCodigoQr("QR-" + id);
        e.setEstado(estado);
        e.setReservaId("r1");
        return e;
    }

    private static Tarifa tarifa(String zona, TipoEntrada tipo, double precio) {
        return new Tarifa("t-" + zona + "-" + tipo, PARTIDO, zona, tipo, precio);
    }

    private static ApiException assertApi(ThrowingCallable call, HttpStatus status) {
        Throwable t = catchThrowable(call);
        assertThat(t).isInstanceOf(ApiException.class);
        ApiException ex = (ApiException) t;
        assertThat(ex.getStatus()).isEqualTo(status);
        return ex;
    }

    private void mockUpdateFirst(Class<?> clase, long modificados) {
        when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(clase)))
                .thenReturn(UpdateResult.acknowledged(modificados, modificados, null));
    }

    private <T> void mockFindAndModify(Class<T> clase, T resultado) {
        when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), eq(clase)))
                .thenReturn(resultado);
    }

    /** Extrae los ids de una condición {_id: {$in: [...]}} (el driver puede guardarlos como array o colección). */
    private static List<Object> idsDelIn(Query query) {
        Object in = ((Document) query.getQueryObject().get("_id")).get("$in");
        return in instanceof Object[] arr ? Arrays.asList(arr) : new ArrayList<Object>((Collection<?>) in);
    }

    /** Verifica que se liberaron (updateMulti) exactamente estos asientos y solo si seguían "reservado". */
    private void verificarLiberados(String... ids) {
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).updateMulti(q.capture(), any(UpdateDefinition.class), eq(Asiento.class));
        assertThat(idsDelIn(q.getValue())).containsExactlyInAnyOrder((Object[]) ids);
        assertThat(q.getValue().getQueryObject().get("estado")).isEqualTo(EstadoAsiento.reservado);
    }

    private void verificarNadaLiberado() {
        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(UpdateDefinition.class), eq(Asiento.class));
    }

    // ─────────────────────────────────────────────
    // ASIENTOS, AFORO Y PRECIOS
    // ─────────────────────────────────────────────

    @Nested
    class Consultas {

        @Test
        void listarAsientos_partidoInexistente_devuelve404() {
            when(asientoRepo.existsByPartidoId(PARTIDO)).thenReturn(false);

            assertApi(() -> service.listarAsientos(PARTIDO, null, null), HttpStatus.NOT_FOUND);
        }

        @Test
        void listarAsientos_sinFiltros() {
            when(asientoRepo.existsByPartidoId(PARTIDO)).thenReturn(true);
            when(asientoRepo.findByPartidoId(PARTIDO))
                    .thenReturn(List.of(asiento("a1", "A", 1, 1, EstadoAsiento.libre)));

            List<AsientoDto> res = service.listarAsientos(PARTIDO, null, null);

            assertThat(res).containsExactly(new AsientoDto("a1", "A", 1, 1, EstadoAsiento.libre));
        }

        @Test
        void listarAsientos_filtraPorZona() {
            when(asientoRepo.existsByPartidoId(PARTIDO)).thenReturn(true);
            when(asientoRepo.findByPartidoIdAndZona(PARTIDO, "B"))
                    .thenReturn(List.of(asiento("a2", "B", 1, 2, EstadoAsiento.ocupado)));

            List<AsientoDto> res = service.listarAsientos(PARTIDO, "B", null);

            assertThat(res).extracting(AsientoDto::asientoId).containsExactly("a2");
            verify(asientoRepo, never()).findByPartidoId(anyString());
        }

        @Test
        void listarAsientos_filtraPorEstado() {
            when(asientoRepo.existsByPartidoId(PARTIDO)).thenReturn(true);
            when(asientoRepo.findByPartidoIdAndEstado(PARTIDO, EstadoAsiento.reservado))
                    .thenReturn(List.of(asiento("a3", "A", 2, 3, EstadoAsiento.reservado)));

            List<AsientoDto> res = service.listarAsientos(PARTIDO, null, EstadoAsiento.reservado);

            assertThat(res).extracting(AsientoDto::asientoId).containsExactly("a3");
        }

        @Test
        void listarAsientos_filtraPorZonaYEstado() {
            when(asientoRepo.existsByPartidoId(PARTIDO)).thenReturn(true);
            when(asientoRepo.findByPartidoIdAndZonaAndEstado(PARTIDO, "A", EstadoAsiento.libre))
                    .thenReturn(List.of(asiento("a4", "A", 1, 4, EstadoAsiento.libre)));

            List<AsientoDto> res = service.listarAsientos(PARTIDO, "A", EstadoAsiento.libre);

            assertThat(res).extracting(AsientoDto::asientoId).containsExactly("a4");
        }

        @Test
        void obtenerAforo_calculaLosLibres() {
            when(asientoRepo.existsByPartidoId(PARTIDO)).thenReturn(true);
            when(asientoRepo.countByPartidoId(PARTIDO)).thenReturn(300L);
            when(asientoRepo.countByPartidoIdAndEstado(PARTIDO, EstadoAsiento.ocupado)).thenReturn(20L);
            when(asientoRepo.countByPartidoIdAndEstado(PARTIDO, EstadoAsiento.reservado)).thenReturn(5L);

            AforoDto aforo = service.obtenerAforo(PARTIDO);

            assertThat(aforo).isEqualTo(new AforoDto(PARTIDO, 300, 20, 5, 275));
        }

        @Test
        void obtenerAforo_partidoInexistente_devuelve404() {
            when(asientoRepo.existsByPartidoId(PARTIDO)).thenReturn(false);

            assertApi(() -> service.obtenerAforo(PARTIDO), HttpStatus.NOT_FOUND);
        }

        @Test
        void listarPrecios_mapeaLasTarifas() {
            when(asientoRepo.existsByPartidoId(PARTIDO)).thenReturn(true);
            when(tarifaRepo.findByPartidoId(PARTIDO)).thenReturn(List.of(
                    tarifa("A", TipoEntrada.general, 8.5),
                    tarifa("A", TipoEntrada.socio, 6.0)));

            List<PrecioDto> precios = service.listarPrecios(PARTIDO);

            assertThat(precios).containsExactly(
                    new PrecioDto("A", TipoEntrada.general, 8.5),
                    new PrecioDto("A", TipoEntrada.socio, 6.0));
        }

        @Test
        void listarPrecios_partidoInexistente_devuelve404() {
            when(asientoRepo.existsByPartidoId(PARTIDO)).thenReturn(false);

            assertApi(() -> service.listarPrecios(PARTIDO), HttpStatus.NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────
    // CREAR RESERVA
    // ─────────────────────────────────────────────

    @Nested
    class CrearReserva {

        private final ReservaCreate req = new ReservaCreate(PARTIDO, List.of("a1", "a2"));

        private void partidoConVentaAbierta() {
            when(deportivoClient.obtenerPartido(PARTIDO))
                    .thenReturn(new DeportivoClient.PartidoInfo(PARTIDO, true));
        }

        @Test
        void bloqueaLosAsientosYGuardaLaReserva() {
            partidoConVentaAbierta();
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 1, 1, EstadoAsiento.libre)));
            when(asientoRepo.findById("a2")).thenReturn(Optional.of(asiento("a2", "A", 1, 2, EstadoAsiento.libre)));
            mockUpdateFirst(Asiento.class, 1);
            Instant antes = Instant.now();

            ReservaDto dto = service.crearReserva(req, USUARIO);

            ArgumentCaptor<Reserva> captor = ArgumentCaptor.forClass(Reserva.class);
            verify(reservaRepo).save(captor.capture());
            Reserva guardada = captor.getValue();
            assertThat(guardada.getReservaId()).isNotBlank();
            assertThat(guardada.getPartidoId()).isEqualTo(PARTIDO);
            assertThat(guardada.getUsuarioId()).isEqualTo(USUARIO);
            assertThat(guardada.getAsientoIds()).containsExactly("a1", "a2");
            assertThat(guardada.getEstado()).isEqualTo(EstadoReserva.activa);
            assertThat(guardada.getExpiraEn()).isBetween(
                    antes.plus(10, ChronoUnit.MINUTES), Instant.now().plus(10, ChronoUnit.MINUTES));

            assertThat(dto.reservaId()).isEqualTo(guardada.getReservaId());
            assertThat(dto.usuarioId()).isEqualTo(USUARIO);
            assertThat(dto.expiraEn()).isEqualTo(guardada.getExpiraEn());
            assertThat(dto.asientos()).extracting(AsientoDto::estado)
                    .containsOnly(EstadoAsiento.reservado);
            verify(mongoTemplate, times(2))
                    .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Asiento.class));
            verificarNadaLiberado();
        }

        @Test
        void elBloqueoSoloSeHaceSiElAsientoSigueLibre() {
            partidoConVentaAbierta();
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 1, 1, EstadoAsiento.libre)));
            mockUpdateFirst(Asiento.class, 1);

            service.crearReserva(new ReservaCreate(PARTIDO, List.of("a1")), USUARIO);

            ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
            verify(mongoTemplate).updateFirst(q.capture(), u.capture(), eq(Asiento.class));
            Document condicion = q.getValue().getQueryObject();
            assertThat(condicion.get("_id")).isEqualTo("a1");
            assertThat(condicion.get("partidoId")).isEqualTo(PARTIDO);
            assertThat(condicion.get("estado")).isEqualTo(EstadoAsiento.libre);
            assertThat(((Document) u.getValue().getUpdateObject().get("$set")).get("estado"))
                    .isEqualTo(EstadoAsiento.reservado);
        }

        @Test
        void partidoInexistenteEnDeportivo_devuelve404() {
            when(deportivoClient.obtenerPartido(PARTIDO))
                    .thenThrow(new DeportivoClient.PartidoNoEncontradoException(PARTIDO));

            assertApi(() -> service.crearReserva(req, USUARIO), HttpStatus.NOT_FOUND);
            verifyNoInteractions(asientoRepo, reservaRepo, mongoTemplate);
        }

        @Test
        void deportivoCaido_devuelve503() {
            when(deportivoClient.obtenerPartido(PARTIDO))
                    .thenThrow(new DeportivoClient.DeportivoNoDisponibleException(new RuntimeException("timeout")));

            assertApi(() -> service.crearReserva(req, USUARIO), HttpStatus.SERVICE_UNAVAILABLE);
            verifyNoInteractions(asientoRepo, reservaRepo, mongoTemplate);
        }

        @Test
        void ventaCerrada_devuelve409() {
            when(deportivoClient.obtenerPartido(PARTIDO))
                    .thenReturn(new DeportivoClient.PartidoInfo(PARTIDO, false));

            assertApi(() -> service.crearReserva(req, USUARIO), HttpStatus.CONFLICT);
            verifyNoInteractions(asientoRepo, reservaRepo, mongoTemplate);
        }

        @Test
        void asientosDuplicados_devuelve400SinTocarNada() {
            partidoConVentaAbierta();

            assertApi(() -> service.crearReserva(new ReservaCreate(PARTIDO, List.of("a1", "a1")), USUARIO),
                    HttpStatus.BAD_REQUEST);
            verifyNoInteractions(asientoRepo, reservaRepo, mongoTemplate);
        }

        @Test
        void asientoInexistente_devuelve404() {
            partidoConVentaAbierta();
            when(asientoRepo.findById("a1")).thenReturn(Optional.empty());

            assertApi(() -> service.crearReserva(req, USUARIO), HttpStatus.NOT_FOUND);
            verify(reservaRepo, never()).save(any(Reserva.class));
            verificarNadaLiberado();
        }

        @Test
        void asientoDeOtroPartido_devuelve404() {
            partidoConVentaAbierta();
            Asiento ajeno = asiento("a1", "A", 1, 1, EstadoAsiento.libre);
            ajeno.setPartidoId("otro-partido");
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(ajeno));

            assertApi(() -> service.crearReserva(req, USUARIO), HttpStatus.NOT_FOUND);
            verify(mongoTemplate, never())
                    .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Asiento.class));
        }

        @Test
        void asientoNoLibre_devuelve409YLiberaLosYaBloqueados() {
            partidoConVentaAbierta();
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 1, 1, EstadoAsiento.libre)));
            when(asientoRepo.findById("a2")).thenReturn(Optional.of(asiento("a2", "A", 1, 2, EstadoAsiento.ocupado)));
            mockUpdateFirst(Asiento.class, 1);

            assertApi(() -> service.crearReserva(req, USUARIO), HttpStatus.CONFLICT);

            verify(reservaRepo, never()).save(any(Reserva.class));
            verificarLiberados("a1");   // todo o nada: a1 se bloqueó y debe volver a libre
        }

        @Test
        void primerAsientoNoLibre_noHaceFaltaLiberarNada() {
            partidoConVentaAbierta();
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 1, 1, EstadoAsiento.reservado)));

            assertApi(() -> service.crearReserva(req, USUARIO), HttpStatus.CONFLICT);

            verificarNadaLiberado();
        }

        @Test
        void carreraPorUnAsiento_laSegundaPeticionPierdeYLiberaLoBloqueado() {
            partidoConVentaAbierta();
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 1, 1, EstadoAsiento.libre)));
            when(asientoRepo.findById("a2")).thenReturn(Optional.of(asiento("a2", "A", 1, 2, EstadoAsiento.libre)));
            // a1 se bloquea (1 documento modificado); a2 ya lo ha cogido otra petición (0 modificados)
            when(mongoTemplate.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Asiento.class)))
                    .thenReturn(UpdateResult.acknowledged(1, 1L, null), UpdateResult.acknowledged(0, 0L, null));

            ApiException ex = assertApi(() -> service.crearReserva(req, USUARIO), HttpStatus.CONFLICT);

            assertThat(ex.getMessage()).contains("a2");
            verify(reservaRepo, never()).save(any(Reserva.class));
            verificarLiberados("a1");
        }

        @Test
        void siFallaGuardarLaReserva_seLiberanLosAsientosYSePropagaElError() {
            partidoConVentaAbierta();
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 1, 1, EstadoAsiento.libre)));
            when(asientoRepo.findById("a2")).thenReturn(Optional.of(asiento("a2", "A", 1, 2, EstadoAsiento.libre)));
            mockUpdateFirst(Asiento.class, 1);
            when(reservaRepo.save(any(Reserva.class))).thenThrow(new IllegalStateException("mongo caído"));

            Throwable t = catchThrowable(() -> service.crearReserva(req, USUARIO));

            assertThat(t).isInstanceOf(IllegalStateException.class).hasMessage("mongo caído");
            verificarLiberados("a1", "a2");
        }
    }

    // ─────────────────────────────────────────────
    // OBTENER / CANCELAR RESERVA
    // ─────────────────────────────────────────────

    @Nested
    class ObtenerYCancelarReserva {

        @Test
        void obtenerReserva_titularVeSuReserva() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaActiva()));
            when(asientoRepo.findAllById(List.of("a1", "a2"))).thenReturn(List.of(
                    asiento("a1", "A", 1, 1, EstadoAsiento.reservado),
                    asiento("a2", "A", 1, 2, EstadoAsiento.reservado)));

            ReservaDto dto = service.obtenerReserva("r1", USUARIO, false);

            assertThat(dto.reservaId()).isEqualTo("r1");
            assertThat(dto.usuarioId()).isEqualTo(USUARIO);
            assertThat(dto.asientos()).hasSize(2);
            assertThat(dto.expiraEn()).isEqualTo(FUTURO);
        }

        @Test
        void obtenerReserva_staffPuedeVerLaDeOtro() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaActiva()));
            when(asientoRepo.findAllById(List.of("a1", "a2"))).thenReturn(List.of());

            ReservaDto dto = service.obtenerReserva("r1", OTRO_USUARIO, true);

            assertThat(dto.usuarioId()).isEqualTo(USUARIO);
        }

        @Test
        void obtenerReserva_otroUsuario_devuelve403() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaActiva()));

            assertApi(() -> service.obtenerReserva("r1", OTRO_USUARIO, false), HttpStatus.FORBIDDEN);
        }

        @Test
        void obtenerReserva_inexistente_devuelve404() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.empty());

            assertApi(() -> service.obtenerReserva("r1", USUARIO, false), HttpStatus.NOT_FOUND);
        }

        @Test
        void cancelar_inexistente_devuelve404() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.empty());

            assertApi(() -> service.cancelarReserva("r1", USUARIO, false), HttpStatus.NOT_FOUND);
        }

        @Test
        void cancelar_deOtroUsuario_devuelve403() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaActiva()));

            assertApi(() -> service.cancelarReserva("r1", OTRO_USUARIO, false), HttpStatus.FORBIDDEN);
            verifyNoInteractions(mongoTemplate);
        }

        @Test
        void cancelar_activa_liberaLosAsientosReservados() {
            Reserva activa = reservaActiva();
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(activa));
            mockFindAndModify(Reserva.class, activa);

            service.cancelarReserva("r1", USUARIO, false);

            ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
            verify(mongoTemplate).findAndModify(q.capture(), u.capture(), eq(Reserva.class));
            assertThat(q.getValue().getQueryObject().get("_id")).isEqualTo("r1");
            assertThat(q.getValue().getQueryObject().get("estado")).isEqualTo(EstadoReserva.activa);
            assertThat(((Document) u.getValue().getUpdateObject().get("$set")).get("estado"))
                    .isEqualTo(EstadoReserva.cancelada);
            verificarLiberados("a1", "a2");
        }

        @Test
        void cancelar_staffPuedeCancelarLaDeOtro() {
            Reserva activa = reservaActiva();
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(activa));
            mockFindAndModify(Reserva.class, activa);

            service.cancelarReserva("r1", OTRO_USUARIO, true);

            verificarLiberados("a1", "a2");
        }

        @ParameterizedTest
        @CsvSource({"pagada", "pagando"})
        void cancelar_conPagoEnCursoOCompletado_devuelve409(EstadoReserva estado) {
            when(reservaRepo.findById("r1"))
                    .thenReturn(Optional.of(reserva("r1", USUARIO, estado, FUTURO, "a1")));

            assertApi(() -> service.cancelarReserva("r1", USUARIO, false), HttpStatus.CONFLICT);
            verifyNoInteractions(mongoTemplate);
        }

        @ParameterizedTest
        @CsvSource({"cancelada", "expirada"})
        void cancelar_yaCanceladaOExpirada_esIdempotente(EstadoReserva estado) {
            when(reservaRepo.findById("r1"))
                    .thenReturn(Optional.of(reserva("r1", USUARIO, estado, FUTURO, "a1")));

            service.cancelarReserva("r1", USUARIO, false);

            verifyNoInteractions(mongoTemplate);
        }

        @Test
        void cancelar_siOtraPeticionLaCanceloMientrasTanto_esIdempotenteYNoLiberaDeNuevo() {
            when(reservaRepo.findById("r1")).thenReturn(
                    Optional.of(reservaActiva()),
                    Optional.of(reserva("r1", USUARIO, EstadoReserva.cancelada, FUTURO, "a1", "a2")));
            mockFindAndModify(Reserva.class, null);

            service.cancelarReserva("r1", USUARIO, false);

            verificarNadaLiberado();
        }

        @Test
        void cancelar_siMientrasTantoSePago_devuelve409() {
            when(reservaRepo.findById("r1")).thenReturn(
                    Optional.of(reservaActiva()),
                    Optional.of(reserva("r1", USUARIO, EstadoReserva.pagada, FUTURO, "a1", "a2")));
            mockFindAndModify(Reserva.class, null);

            assertApi(() -> service.cancelarReserva("r1", USUARIO, false), HttpStatus.CONFLICT);
            verificarNadaLiberado();
        }
    }

    // ─────────────────────────────────────────────
    // COMPRAR ENTRADAS
    // ─────────────────────────────────────────────

    @Nested
    class ComprarEntradas {

        private void stubAsientosYTarifas() {
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 1, 1, EstadoAsiento.reservado)));
            when(asientoRepo.findById("a2")).thenReturn(Optional.of(asiento("a2", "B", 2, 5, EstadoAsiento.reservado)));
            when(tarifaRepo.findByPartidoIdAndZonaAndTipoEntrada(PARTIDO, "A", TipoEntrada.general))
                    .thenReturn(Optional.of(tarifa("A", TipoEntrada.general, 8.5)));
            when(tarifaRepo.findByPartidoIdAndZonaAndTipoEntrada(PARTIDO, "B", TipoEntrada.general))
                    .thenReturn(Optional.of(tarifa("B", TipoEntrada.general, 6.0)));
            when(entradaRepo.findByReservaIdAndAsientoId(eq("r1"), anyString())).thenReturn(Optional.empty());
            when(entradaRepo.findByReservaId("r1")).thenReturn(List.of(
                    entrada("e1", "a1", EstadoEntrada.valida), entrada("e2", "a2", EstadoEntrada.valida)));
            mockUpdateFirst(Asiento.class, 1);
            mockUpdateFirst(Reserva.class, 1);
        }

        /**
         * Compra sobre una reserva activa. La primera lectura devuelve la reserva "activa" y la segunda
         * (que hace la emisión) la reserva ya "reclamada" con el descuento indicado.
         */
        private void stubCompraSobreReservaActiva(UsuariosClient.SocioInfo socio, double descuentoGuardado) {
            when(reservaRepo.findById("r1")).thenReturn(
                    Optional.of(reservaActiva()),
                    Optional.of(reservaPagando(descuentoGuardado, Instant.now())));
            when(usuariosClient.obtenerInfoSocio(USUARIO)).thenReturn(socio);
            mockFindAndModify(Reserva.class, reservaActiva());
            stubAsientosYTarifas();
        }

        private Document setDeLaReclamacion() {
            ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
            verify(mongoTemplate).findAndModify(any(Query.class), u.capture(), eq(Reserva.class));
            return (Document) u.getValue().getUpdateObject().get("$set");
        }

        // ── validaciones previas ──────────────────

        @Test
        void reservaInexistente_devuelve404() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.empty());

            assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), HttpStatus.NOT_FOUND);
        }

        @Test
        void soloElTitularPuedeComprar() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaActiva()));

            assertApi(() -> service.comprarEntradas(COMPRA, OTRO_USUARIO), HttpStatus.FORBIDDEN);
            verifyNoInteractions(usuariosClient, mongoTemplate);
        }

        @Test
        void reservaCancelada_devuelve409() {
            when(reservaRepo.findById("r1")).thenReturn(
                    Optional.of(reserva("r1", USUARIO, EstadoReserva.cancelada, FUTURO, "a1")));

            assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), HttpStatus.CONFLICT);
        }

        @Test
        void reservaExpirada_devuelve410() {
            when(reservaRepo.findById("r1")).thenReturn(
                    Optional.of(reserva("r1", USUARIO, EstadoReserva.expirada, FUTURO, "a1")));

            assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), HttpStatus.GONE);
        }

        @Test
        void reservaActivaPeroVencidaPorTiempo_devuelve410YLiberaLosAsientos() {
            Reserva vencida = reserva("r1", USUARIO, EstadoReserva.activa,
                    Instant.now().minusSeconds(5), "a1", "a2");
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(vencida));
            mockFindAndModify(Reserva.class, vencida);

            assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), HttpStatus.GONE);

            verify(usuariosClient, never()).obtenerInfoSocio(anyString());
            verificarLiberados("a1", "a2");
        }

        // ── reintentos sobre una reserva ya pagada ──

        @Test
        void reservaPagadaConLosMismosDatos_devuelveLasMismasEntradasSinCobrarDeNuevo() {
            Reserva pagada = reservaPagando(0.0, null);
            pagada.setEstado(EstadoReserva.pagada);
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(pagada));
            when(entradaRepo.findByReservaId("r1")).thenReturn(List.of(
                    entrada("e1", "a1", EstadoEntrada.valida), entrada("e2", "a2", EstadoEntrada.valida)));

            List<EntradaDto> res = service.comprarEntradas(COMPRA, USUARIO);

            assertThat(res).extracting(EntradaDto::entradaId).containsExactly("e1", "e2");
            verifyNoInteractions(usuariosClient, mongoTemplate);
            verify(entradaRepo, never()).save(any(Entrada.class));
        }

        @Test
        void reservaPagadaConDatosDistintos_devuelve409() {
            Reserva pagada = reservaPagando(0.0, null);
            pagada.setEstado(EstadoReserva.pagada);
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(pagada));

            assertApi(() -> service.comprarEntradas(
                    new CompraEntradas("r1", TipoEntrada.vip, MetodoPago.tarjeta), USUARIO), HttpStatus.CONFLICT);
            assertApi(() -> service.comprarEntradas(
                    new CompraEntradas("r1", TipoEntrada.general, MetodoPago.bizum), USUARIO), HttpStatus.CONFLICT);
        }

        // ── Usuarios: socio y descuento ───────────

        @Test
        void usuarioNoEncontradoEnUsuarios_devuelve404() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaActiva()));
            when(usuariosClient.obtenerInfoSocio(USUARIO))
                    .thenThrow(new UsuariosClient.UsuarioNoEncontradoException(USUARIO));

            assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), HttpStatus.NOT_FOUND);
            verifyNoInteractions(mongoTemplate);
        }

        @Test
        void usuariosCaido_devuelve503() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaActiva()));
            when(usuariosClient.obtenerInfoSocio(USUARIO))
                    .thenThrow(new UsuariosClient.UsuariosNoDisponibleException(new RuntimeException("timeout")));

            assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), HttpStatus.SERVICE_UNAVAILABLE);
            verifyNoInteractions(mongoTemplate);
        }

        @Test
        void entradaDeTipoSocio_siElUsuarioNoEsSocioActivo_devuelve400() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaActiva()));
            when(usuariosClient.obtenerInfoSocio(USUARIO))
                    .thenReturn(new UsuariosClient.SocioInfo(USUARIO, "caducado", 0.0));

            assertApi(() -> service.comprarEntradas(
                    new CompraEntradas("r1", TipoEntrada.socio, MetodoPago.tarjeta), USUARIO),
                    HttpStatus.BAD_REQUEST);
            verifyNoInteractions(mongoTemplate);
        }

        @ParameterizedTest
        @CsvSource({
                "activo,   15.0, 15.0",
                "ACTIVO,   15.0, 15.0",
                "caducado, 20.0,  0.0",
                "no_socio,  0.0,  0.0"
        })
        void soloElSocioActivoRecibeDescuento(String estado, double descuentoDeUsuarios, double descuentoEsperado) {
            stubCompraSobreReservaActiva(
                    new UsuariosClient.SocioInfo(USUARIO, estado, descuentoDeUsuarios), descuentoEsperado);

            service.comprarEntradas(COMPRA, USUARIO);

            assertThat(setDeLaReclamacion().get("descuento")).isEqualTo(descuentoEsperado);
        }

        // ── tarifas ───────────────────────────────

        @Test
        void siFaltaUnaTarifa_devuelve400SinTocarElEstadoDeLaReserva() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaActiva()));
            when(usuariosClient.obtenerInfoSocio(USUARIO))
                    .thenReturn(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0));
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 1, 1, EstadoAsiento.reservado)));
            when(asientoRepo.findById("a2")).thenReturn(Optional.of(asiento("a2", "B", 2, 5, EstadoAsiento.reservado)));
            when(tarifaRepo.findByPartidoIdAndZonaAndTipoEntrada(PARTIDO, "A", TipoEntrada.general))
                    .thenReturn(Optional.of(tarifa("A", TipoEntrada.general, 8.5)));
            when(tarifaRepo.findByPartidoIdAndZonaAndTipoEntrada(PARTIDO, "B", TipoEntrada.general))
                    .thenReturn(Optional.empty());

            ApiException ex = assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), HttpStatus.BAD_REQUEST);

            assertThat(ex.getMessage()).contains("B").contains("general");
            verifyNoInteractions(mongoTemplate);   // no se llegó a "reclamar" la reserva
            verify(entradaRepo, never()).save(any(Entrada.class));
        }

        @Test
        void siUnAsientoDeLaReservaYaNoExiste_devuelve404() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaActiva()));
            when(usuariosClient.obtenerInfoSocio(USUARIO))
                    .thenReturn(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0));
            when(asientoRepo.findById("a1")).thenReturn(Optional.empty());

            assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), HttpStatus.NOT_FOUND);
            verifyNoInteractions(mongoTemplate);
        }

        // ── compra correcta ───────────────────────

        @Test
        void compraCorrecta_emiteUnaEntradaPorAsientoConElPrecioYDescuentoCorrectos() {
            stubCompraSobreReservaActiva(new UsuariosClient.SocioInfo(USUARIO, "activo", 10.0), 10.0);

            List<EntradaDto> res = service.comprarEntradas(COMPRA, USUARIO);

            assertThat(res).hasSize(2);

            ArgumentCaptor<Entrada> captor = ArgumentCaptor.forClass(Entrada.class);
            verify(entradaRepo, times(2)).save(captor.capture());
            List<Entrada> emitidas = captor.getAllValues();

            assertThat(emitidas).extracting(Entrada::getAsientoId).containsExactly("a1", "a2");
            // 8,50 € con 10 % → 7,65 €; 6,00 € con 10 % → 5,40 €
            assertThat(emitidas.get(0).getPrecioPagado()).isCloseTo(7.65, within(0.001));
            assertThat(emitidas.get(1).getPrecioPagado()).isCloseTo(5.40, within(0.001));
            assertThat(emitidas).allSatisfy(e -> {
                assertThat(e.getEntradaId()).isNotBlank();
                assertThat(e.getReservaId()).isEqualTo("r1");
                assertThat(e.getPartidoId()).isEqualTo(PARTIDO);
                assertThat(e.getUsuarioId()).isEqualTo(USUARIO);
                assertThat(e.getTipoEntrada()).isEqualTo(TipoEntrada.general);
                assertThat(e.getMetodoPago()).isEqualTo(MetodoPago.tarjeta);
                assertThat(e.getDescuentoAplicado()).isEqualTo(10.0);
                assertThat(e.getEstado()).isEqualTo(EstadoEntrada.valida);
                assertThat(e.isEventoPublicado()).isFalse();
                assertThat(e.getCodigoQr()).matches("QR-[0-9A-F]{16}");
            });
            assertThat(emitidas).extracting(Entrada::getEntradaId).doesNotHaveDuplicates();
            assertThat(emitidas).extracting(Entrada::getCodigoQr).doesNotHaveDuplicates();
        }

        @Test
        void compraCorrecta_sinDescuento_cobraLaTarifaCompleta() {
            stubCompraSobreReservaActiva(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0), 0.0);

            service.comprarEntradas(COMPRA, USUARIO);

            ArgumentCaptor<Entrada> captor = ArgumentCaptor.forClass(Entrada.class);
            verify(entradaRepo, times(2)).save(captor.capture());
            assertThat(captor.getAllValues()).extracting(Entrada::getPrecioPagado).containsExactly(8.5, 6.0);
        }

        @Test
        void compraCorrecta_reclamaLaReservaDeFormaAtomicaGuardandoLosDatosDeLaCompra() {
            stubCompraSobreReservaActiva(new UsuariosClient.SocioInfo(USUARIO, "activo", 10.0), 10.0);

            service.comprarEntradas(COMPRA, USUARIO);

            ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
            verify(mongoTemplate).findAndModify(q.capture(), u.capture(), eq(Reserva.class));
            // condición: sigue activa (y no ha caducado)
            assertThat(q.getValue().getQueryObject().get("_id")).isEqualTo("r1");
            assertThat(q.getValue().getQueryObject().get("estado")).isEqualTo(EstadoReserva.activa);
            assertThat(q.getValue().getQueryObject().containsKey("expiraEn")).isTrue();
            // efecto: pasa a "pagando" y recuerda los datos para detectar reintentos
            Document set = (Document) u.getValue().getUpdateObject().get("$set");
            assertThat(set.get("estado")).isEqualTo(EstadoReserva.pagando);
            assertThat(set.get("tipoEntrada")).isEqualTo(TipoEntrada.general);
            assertThat(set.get("metodoPago")).isEqualTo(MetodoPago.tarjeta);
            assertThat(set.get("descuento")).isEqualTo(10.0);
            assertThat(set.get("pagandoDesde")).isInstanceOf(Instant.class);
        }

        @Test
        void compraCorrecta_marcaAsientosOcupadosYLaReservaPagada() {
            stubCompraSobreReservaActiva(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0), 0.0);

            service.comprarEntradas(COMPRA, USUARIO);

            // asientos: reservado → ocupado (uno por cada asiento)
            ArgumentCaptor<Query> qa = ArgumentCaptor.forClass(Query.class);
            verify(mongoTemplate, times(2)).updateFirst(qa.capture(), any(UpdateDefinition.class), eq(Asiento.class));
            assertThat(qa.getAllValues()).extracting(q -> q.getQueryObject().get("_id"))
                    .containsExactly("a1", "a2");
            assertThat(qa.getAllValues()).extracting(q -> q.getQueryObject().get("estado"))
                    .containsOnly(EstadoAsiento.reservado);

            // reserva: pagando → pagada
            ArgumentCaptor<Query> qr = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<UpdateDefinition> ur = ArgumentCaptor.forClass(UpdateDefinition.class);
            verify(mongoTemplate).updateFirst(qr.capture(), ur.capture(), eq(Reserva.class));
            assertThat(qr.getValue().getQueryObject().get("estado")).isEqualTo(EstadoReserva.pagando);
            assertThat(((Document) ur.getValue().getUpdateObject().get("$set")).get("estado"))
                    .isEqualTo(EstadoReserva.pagada);
        }

        @Test
        void compraCorrecta_publicaUnEventoPorEntradaYLasMarcaComoPublicadas() {
            stubCompraSobreReservaActiva(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0), 0.0);
            when(entradaRepo.findByEventoPublicadoFalseAndEstadoNot(EstadoEntrada.anulada)).thenReturn(List.of(
                    entrada("e1", "a1", EstadoEntrada.valida), entrada("e2", "a2", EstadoEntrada.valida)));

            service.comprarEntradas(COMPRA, USUARIO);

            verify(eventPublisher, times(2)).publicarEntradaComprada(any(EntradaEventPublisher.EntradaCompradaEvent.class));
            verify(mongoTemplate, times(2))
                    .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Entrada.class));
        }

        @Test
        void siRabbitFalla_laCompraSigueSiendoValida() {
            stubCompraSobreReservaActiva(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0), 0.0);
            when(entradaRepo.findByEventoPublicadoFalseAndEstadoNot(EstadoEntrada.anulada))
                    .thenReturn(List.of(entrada("e1", "a1", EstadoEntrada.valida)));
            doThrow(new IllegalStateException("broker caído")).when(eventPublisher)
                    .publicarEntradaComprada(any(EntradaEventPublisher.EntradaCompradaEvent.class));

            List<EntradaDto> res = service.comprarEntradas(COMPRA, USUARIO);

            assertThat(res).hasSize(2);
            // el evento queda sin marcar para que el job periódico lo reintente
            verify(mongoTemplate, never())
                    .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Entrada.class));
        }

        @Test
        void siYaExisteLaEntradaDeUnAsiento_noSeDuplica() {
            stubCompraSobreReservaActiva(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0), 0.0);
            when(entradaRepo.findByReservaIdAndAsientoId("r1", "a1"))
                    .thenReturn(Optional.of(entrada("e1", "a1", EstadoEntrada.valida)));

            service.comprarEntradas(COMPRA, USUARIO);

            ArgumentCaptor<Entrada> captor = ArgumentCaptor.forClass(Entrada.class);
            verify(entradaRepo, times(1)).save(captor.capture());
            assertThat(captor.getValue().getAsientoId()).isEqualTo("a2");
        }

        @Test
        void siSaltaElIndiceUnico_seIgnoraYSeContinua() {
            stubCompraSobreReservaActiva(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0), 0.0);
            when(entradaRepo.save(any(Entrada.class))).thenThrow(new DuplicateKeyException("uk_reserva_asiento"));

            List<EntradaDto> res = service.comprarEntradas(COMPRA, USUARIO);

            assertThat(res).hasSize(2);
            verify(mongoTemplate, times(2))
                    .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Asiento.class));
        }

        // ── la reserva no se pudo reclamar ────────

        @ParameterizedTest
        @CsvSource({
                "cancelada, CONFLICT",
                "expirada,  GONE",
                "activa,    GONE",
                "pagando,   CONFLICT",
                "pagada,    CONFLICT"
        })
        void siOtraPeticionSeAdelanto_elErrorDependeDelEstadoActual(EstadoReserva actual, HttpStatus esperado) {
            when(reservaRepo.findById("r1")).thenReturn(
                    Optional.of(reservaActiva()),
                    Optional.of(reserva("r1", USUARIO, actual, FUTURO, "a1", "a2")));
            when(usuariosClient.obtenerInfoSocio(USUARIO))
                    .thenReturn(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0));
            stubAsientosYTarifas();
            mockFindAndModify(Reserva.class, null);   // la reclamación atómica no encontró la reserva activa

            assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), esperado);
            verify(entradaRepo, never()).save(any(Entrada.class));
        }

        // ── fallo durante la emisión ──────────────

        @Test
        void siFallaLaEmisionSinCrearNingunaEntrada_laReservaVuelveAActiva() {
            stubCompraSobreReservaActiva(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0), 0.0);
            when(entradaRepo.save(any(Entrada.class))).thenThrow(new IllegalStateException("mongo caído"));
            when(entradaRepo.findByReservaId("r1")).thenReturn(List.of());

            Throwable t = catchThrowable(() -> service.comprarEntradas(COMPRA, USUARIO));

            assertThat(t).isInstanceOf(IllegalStateException.class);
            ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
            verify(mongoTemplate).updateFirst(q.capture(), u.capture(), eq(Reserva.class));
            assertThat(q.getValue().getQueryObject().get("estado")).isEqualTo(EstadoReserva.pagando);
            assertThat(((Document) u.getValue().getUpdateObject().get("$set")).get("estado"))
                    .isEqualTo(EstadoReserva.activa);
        }

        @Test
        void siFallaLaEmisionConEntradasAMedias_laReservaSeQuedaEnPagando() {
            stubCompraSobreReservaActiva(new UsuariosClient.SocioInfo(USUARIO, "no_socio", 0.0), 0.0);
            when(entradaRepo.save(any(Entrada.class))).thenThrow(new IllegalStateException("mongo caído"));
            when(entradaRepo.findByReservaId("r1"))
                    .thenReturn(List.of(entrada("e1", "a1", EstadoEntrada.valida)));

            Throwable t = catchThrowable(() -> service.comprarEntradas(COMPRA, USUARIO));

            assertThat(t).isInstanceOf(IllegalStateException.class);
            verify(mongoTemplate, never())
                    .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Reserva.class));
        }

        // ── reanudar un pago interrumpido ─────────

        @Test
        void reservaPagando_conDatosDistintos_devuelve409() {
            when(reservaRepo.findById("r1")).thenReturn(Optional.of(reservaPagando(0.0, Instant.now())));

            assertApi(() -> service.comprarEntradas(
                    new CompraEntradas("r1", TipoEntrada.vip, MetodoPago.tarjeta), USUARIO), HttpStatus.CONFLICT);
        }

        @Test
        void reservaPagando_conPagoRecienIniciado_devuelve409PagoEnCurso() {
            when(reservaRepo.findById("r1"))
                    .thenReturn(Optional.of(reservaPagando(0.0, Instant.now().minusSeconds(30))));

            ApiException ex = assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), HttpStatus.CONFLICT);

            assertThat(ex.getMessage()).containsIgnoringCase("en curso");
            verifyNoInteractions(mongoTemplate);
            verify(entradaRepo, never()).save(any(Entrada.class));
        }

        @Test
        void reservaPagando_conElLeaseVencido_seReanudaYSeCompletaLaEmision() {
            when(reservaRepo.findById("r1"))
                    .thenReturn(Optional.of(reservaPagando(0.0, Instant.now().minus(5, ChronoUnit.MINUTES))));
            mockFindAndModify(Reserva.class, reservaPagando(0.0, null));
            stubAsientosYTarifas();

            List<EntradaDto> res = service.comprarEntradas(COMPRA, USUARIO);

            assertThat(res).hasSize(2);
            verify(entradaRepo, times(2)).save(any(Entrada.class));
            // no se vuelve a consultar Usuarios: el descuento ya quedó guardado en la reserva
            verifyNoInteractions(usuariosClient);
        }

        @Test
        void reservaPagando_siOtraPeticionReanudaPrimero_devuelve409() {
            when(reservaRepo.findById("r1"))
                    .thenReturn(Optional.of(reservaPagando(0.0, Instant.now().minus(5, ChronoUnit.MINUTES))));
            mockFindAndModify(Reserva.class, null);

            assertApi(() -> service.comprarEntradas(COMPRA, USUARIO), HttpStatus.CONFLICT);
            verify(entradaRepo, never()).save(any(Entrada.class));
        }
    }

    // ─────────────────────────────────────────────
    // CONSULTA, ANULACIÓN Y VALIDACIÓN DE ENTRADAS
    // ─────────────────────────────────────────────

    @Nested
    class Entradas {

        @Test
        void listarEntradas_deUnUsuario() {
            when(entradaRepo.findByUsuarioId(USUARIO))
                    .thenReturn(List.of(entrada("e1", "a1", EstadoEntrada.valida)));
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 1, 1, EstadoAsiento.ocupado)));

            List<EntradaDto> res = service.listarEntradas(USUARIO, null, USUARIO, false);

            assertThat(res).hasSize(1);
            assertThat(res.get(0).asiento().zona()).isEqualTo("A");
        }

        @Test
        void listarEntradas_conFiltroDePartido() {
            when(entradaRepo.findByUsuarioIdAndPartidoId(USUARIO, PARTIDO))
                    .thenReturn(List.of(entrada("e1", "a1", EstadoEntrada.valida)));
            when(asientoRepo.findById("a1")).thenReturn(Optional.empty());

            List<EntradaDto> res = service.listarEntradas(USUARIO, PARTIDO, USUARIO, false);

            assertThat(res).hasSize(1);
            assertThat(res.get(0).asiento()).isNull();   // asiento desaparecido: no falla
            verify(entradaRepo, never()).findByUsuarioId(anyString());
        }

        @Test
        void listarEntradas_noSePuedenVerLasDeOtroUsuario() {
            assertApi(() -> service.listarEntradas(USUARIO, null, OTRO_USUARIO, false), HttpStatus.FORBIDDEN);
            verifyNoInteractions(entradaRepo);
        }

        @Test
        void listarEntradas_staffPuedeVerLasDeCualquiera() {
            when(entradaRepo.findByUsuarioId(USUARIO)).thenReturn(List.of());

            assertThat(service.listarEntradas(USUARIO, null, OTRO_USUARIO, true)).isEmpty();
        }

        @Test
        void obtenerEntrada_titular() {
            when(entradaRepo.findById("e1")).thenReturn(Optional.of(entrada("e1", "a1", EstadoEntrada.valida)));
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 3, 7, EstadoAsiento.ocupado)));

            EntradaDto dto = service.obtenerEntrada("e1", USUARIO, false);

            assertThat(dto.entradaId()).isEqualTo("e1");
            assertThat(dto.codigoQr()).isEqualTo("QR-e1");
            assertThat(dto.asiento()).isEqualTo(new AsientoDto("a1", "A", 3, 7, EstadoAsiento.ocupado));
        }

        @Test
        void obtenerEntrada_otroUsuario_devuelve403_yStaffPuede() {
            when(entradaRepo.findById("e1")).thenReturn(Optional.of(entrada("e1", "a1", EstadoEntrada.valida)));

            assertApi(() -> service.obtenerEntrada("e1", OTRO_USUARIO, false), HttpStatus.FORBIDDEN);
            assertThat(service.obtenerEntrada("e1", OTRO_USUARIO, true).entradaId()).isEqualTo("e1");
        }

        @Test
        void obtenerEntrada_inexistente_devuelve404() {
            when(entradaRepo.findById("e1")).thenReturn(Optional.empty());

            assertApi(() -> service.obtenerEntrada("e1", USUARIO, false), HttpStatus.NOT_FOUND);
        }

        // ── anular ────────────────────────────────

        @Test
        void anular_sinSerStaff_devuelve403() {
            assertApi(() -> service.anularEntrada("e1", false), HttpStatus.FORBIDDEN);
            verifyNoInteractions(mongoTemplate);
        }

        @Test
        void anular_marcaAnuladaYLiberaElAsiento() {
            mockFindAndModify(Entrada.class, entrada("e1", "a1", EstadoEntrada.valida));

            service.anularEntrada("e1", true);

            ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
            verify(mongoTemplate).updateFirst(q.capture(), u.capture(), eq(Asiento.class));
            assertThat(q.getValue().getQueryObject().get("_id")).isEqualTo("a1");
            assertThat(q.getValue().getQueryObject().get("estado")).isEqualTo(EstadoAsiento.ocupado);
            assertThat(((Document) u.getValue().getUpdateObject().get("$set")).get("estado"))
                    .isEqualTo(EstadoAsiento.libre);
        }

        @Test
        void anular_yaAnulada_esIdempotenteYNoLiberaDeNuevo() {
            mockFindAndModify(Entrada.class, null);
            when(entradaRepo.findById("e1")).thenReturn(Optional.of(entrada("e1", "a1", EstadoEntrada.anulada)));

            service.anularEntrada("e1", true);

            verify(mongoTemplate, never())
                    .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Asiento.class));
        }

        @Test
        void anular_yaUsada_devuelve409() {
            mockFindAndModify(Entrada.class, null);
            when(entradaRepo.findById("e1")).thenReturn(Optional.of(entrada("e1", "a1", EstadoEntrada.usada)));

            assertApi(() -> service.anularEntrada("e1", true), HttpStatus.CONFLICT);
        }

        @Test
        void anular_inexistente_devuelve404() {
            mockFindAndModify(Entrada.class, null);
            when(entradaRepo.findById("e1")).thenReturn(Optional.empty());

            assertApi(() -> service.anularEntrada("e1", true), HttpStatus.NOT_FOUND);
        }

        // ── validar en el acceso ──────────────────

        @Test
        void validar_sinSerStaff_devuelve403() {
            assertApi(() -> service.validarEntrada("e1", false), HttpStatus.FORBIDDEN);
            verifyNoInteractions(mongoTemplate);
        }

        @Test
        void validar_marcaLaEntradaComoUsadaYLaDevuelve() {
            when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                    any(FindAndModifyOptions.class), eq(Entrada.class)))
                    .thenReturn(entrada("e1", "a1", EstadoEntrada.usada));
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 1, 1, EstadoAsiento.ocupado)));

            EntradaDto dto = service.validarEntrada("e1", true);

            assertThat(dto.entradaId()).isEqualTo("e1");
            assertThat(dto.estado()).isEqualTo(EstadoEntrada.usada);

            ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<FindAndModifyOptions> o = ArgumentCaptor.forClass(FindAndModifyOptions.class);
            verify(mongoTemplate).findAndModify(q.capture(), any(UpdateDefinition.class), o.capture(), eq(Entrada.class));
            assertThat(q.getValue().getQueryObject().get("estado")).isEqualTo(EstadoEntrada.valida);
            assertThat(o.getValue().isReturnNew()).isTrue();
        }

        @Test
        void validar_entradaYaUsadaOAnulada_devuelve409() {
            when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                    any(FindAndModifyOptions.class), eq(Entrada.class))).thenReturn(null);
            when(entradaRepo.findById("e1")).thenReturn(Optional.of(entrada("e1", "a1", EstadoEntrada.usada)));

            assertApi(() -> service.validarEntrada("e1", true), HttpStatus.CONFLICT);
        }

        @Test
        void validar_entradaInexistente_devuelve404() {
            when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                    any(FindAndModifyOptions.class), eq(Entrada.class))).thenReturn(null);
            when(entradaRepo.findById("e1")).thenReturn(Optional.empty());

            assertApi(() -> service.validarEntrada("e1", true), HttpStatus.NOT_FOUND);
        }
    }

    // ─────────────────────────────────────────────
    // TAREAS PROGRAMADAS
    // ─────────────────────────────────────────────

    @Nested
    class Schedulers {

        @Test
        void limpiarReservasExpiradas_liberaLosAsientosDeLasQueExpira() {
            Reserva r1 = reserva("r1", USUARIO, EstadoReserva.activa, Instant.now().minusSeconds(60), "a1", "a2");
            when(reservaRepo.findByEstadoAndExpiraEnBefore(eq(EstadoReserva.activa), any(Instant.class)))
                    .thenReturn(List.of(r1));
            mockFindAndModify(Reserva.class, r1);

            service.limpiarReservasExpiradas();

            ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
            verify(mongoTemplate).findAndModify(any(Query.class), u.capture(), eq(Reserva.class));
            assertThat(((Document) u.getValue().getUpdateObject().get("$set")).get("estado"))
                    .isEqualTo(EstadoReserva.expirada);
            verificarLiberados("a1", "a2");
        }

        @Test
        void limpiarReservasExpiradas_noLiberaSiOtraPeticionLaReclamoAntes() {
            Reserva r1 = reserva("r1", USUARIO, EstadoReserva.activa, Instant.now().minusSeconds(60), "a1");
            when(reservaRepo.findByEstadoAndExpiraEnBefore(eq(EstadoReserva.activa), any(Instant.class)))
                    .thenReturn(List.of(r1));
            mockFindAndModify(Reserva.class, null);   // ya está "pagando" o cancelada

            service.limpiarReservasExpiradas();

            verificarNadaLiberado();
        }

        @Test
        void limpiarReservasExpiradas_unaReservaQueFallaNoImpideProcesarLasDemas() {
            Reserva r1 = reserva("r1", USUARIO, EstadoReserva.activa, Instant.now().minusSeconds(60), "a1");
            Reserva r2 = reserva("r2", USUARIO, EstadoReserva.activa, Instant.now().minusSeconds(60), "a2");
            when(reservaRepo.findByEstadoAndExpiraEnBefore(eq(EstadoReserva.activa), any(Instant.class)))
                    .thenReturn(List.of(r1, r2));
            when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class), eq(Reserva.class)))
                    .thenThrow(new IllegalStateException("mongo caído"))
                    .thenReturn(r2);

            service.limpiarReservasExpiradas();

            verificarLiberados("a2");
        }

        @Test
        void publicarEventosPendientes_publicaYMarcaCadaEntrada() {
            when(entradaRepo.findByEventoPublicadoFalseAndEstadoNot(EstadoEntrada.anulada)).thenReturn(List.of(
                    entrada("e1", "a1", EstadoEntrada.valida), entrada("e2", "a2", EstadoEntrada.valida)));
            when(asientoRepo.findById("a1")).thenReturn(Optional.of(asiento("a1", "A", 3, 7, EstadoAsiento.ocupado)));
            when(asientoRepo.findById("a2")).thenReturn(Optional.of(asiento("a2", "B", 1, 2, EstadoAsiento.ocupado)));

            service.publicarEventosPendientes();

            ArgumentCaptor<EntradaEventPublisher.EntradaCompradaEvent> ev =
                    ArgumentCaptor.forClass(EntradaEventPublisher.EntradaCompradaEvent.class);
            verify(eventPublisher, times(2)).publicarEntradaComprada(ev.capture());
            EntradaEventPublisher.EntradaCompradaEvent primero = ev.getAllValues().get(0);
            assertThat(primero.referenciaId()).isEqualTo("e1");
            assertThat(primero.usuarioId()).isEqualTo(USUARIO);
            assertThat(primero.importe()).isEqualTo(8.5);
            assertThat(primero.descripcion()).isEqualTo("Entrada zona A, fila 3, asiento 7");

            ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
            ArgumentCaptor<UpdateDefinition> u = ArgumentCaptor.forClass(UpdateDefinition.class);
            verify(mongoTemplate, times(2)).updateFirst(q.capture(), u.capture(), eq(Entrada.class));
            assertThat(q.getAllValues()).extracting(x -> x.getQueryObject().get("_id")).containsExactly("e1", "e2");
            assertThat(u.getAllValues()).allSatisfy(x ->
                    assertThat(((Document) x.getUpdateObject().get("$set")).get("eventoPublicado")).isEqualTo(true));
        }

        @Test
        void publicarEventosPendientes_siElAsientoNoExiste_usaValoresPorDefecto() {
            when(entradaRepo.findByEventoPublicadoFalseAndEstadoNot(EstadoEntrada.anulada))
                    .thenReturn(List.of(entrada("e1", "a1", EstadoEntrada.valida)));
            when(asientoRepo.findById("a1")).thenReturn(Optional.empty());

            service.publicarEventosPendientes();

            ArgumentCaptor<EntradaEventPublisher.EntradaCompradaEvent> ev =
                    ArgumentCaptor.forClass(EntradaEventPublisher.EntradaCompradaEvent.class);
            verify(eventPublisher).publicarEntradaComprada(ev.capture());
            assertThat(ev.getValue().descripcion()).isEqualTo("Entrada zona , fila 0, asiento 0");
        }

        @Test
        void publicarEventosPendientes_siElBrokerEstaCaido_paraALaPrimeraYNoMarcaNada() {
            when(entradaRepo.findByEventoPublicadoFalseAndEstadoNot(EstadoEntrada.anulada)).thenReturn(List.of(
                    entrada("e1", "a1", EstadoEntrada.valida), entrada("e2", "a2", EstadoEntrada.valida)));
            when(asientoRepo.findById(anyString())).thenReturn(Optional.empty());
            doThrow(new IllegalStateException("broker caído")).when(eventPublisher)
                    .publicarEntradaComprada(any(EntradaEventPublisher.EntradaCompradaEvent.class));

            service.publicarEventosPendientes();   // no debe lanzar

            // no insiste con la segunda: evitaría esperar un timeout por cada entrada
            verify(eventPublisher, times(1))
                    .publicarEntradaComprada(any(EntradaEventPublisher.EntradaCompradaEvent.class));
            verify(mongoTemplate, never())
                    .updateFirst(any(Query.class), any(UpdateDefinition.class), eq(Entrada.class));
        }

        @Test
        void publicarEventosPendientes_sinPendientes_noHaceNada() {
            when(entradaRepo.findByEventoPublicadoFalseAndEstadoNot(EstadoEntrada.anulada)).thenReturn(List.of());

            service.publicarEventosPendientes();

            verifyNoInteractions(eventPublisher, mongoTemplate);
        }
    }
}
