package com.asee.entradas.service;

import com.asee.entradas.client.DeportivoClient;
import com.asee.entradas.client.UsuariosClient;
import com.asee.entradas.config.EntradasProperties;
import com.asee.entradas.dto.*;
import com.asee.entradas.entity.*;
import com.asee.entradas.exception.ApiException;
import com.asee.entradas.messaging.EntradaEventPublisher;
import com.asee.entradas.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Servicio principal del microservicio de Entradas.
 *
 * Implementa:
 * - Consulta de asientos, aforo y precios
 * - Reserva atómica de asientos (evita doble compra)
 * - Compra de entradas con consulta síncrona a Deportivo y Usuarios
 * - Publicación del evento "entrada comprada" en RabbitMQ (con reintentos)
 * - Limpieza automática de reservas expiradas
 *
 * IDEA CLAVE: no se usan locks en Java. Cada cambio de estado se hace con una
 * operación atómica de MongoDB (updateFirst / findAndModify con el estado esperado
 * en la condición). Si dos peticiones compiten, solo una "gana" la transición.
 *
 *   Asiento:  libre → reservado → ocupado   (y vuelta a libre al cancelar/expirar/anular)
 *   Reserva:  activa → pagando → pagada
 *             activa → cancelada | expirada
 *   Entrada:  valida → usada | anulada
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntradasService {

    /** Tiempo tras el cual un pago "en curso" que no terminó se considera interrumpido y puede reanudarse. */
    private static final Duration LEASE_PAGO = Duration.ofMinutes(2);

    private final AsientoRepository asientoRepo;
    private final ReservaRepository reservaRepo;
    private final EntradaRepository entradaRepo;
    private final TarifaRepository tarifaRepo;
    private final EntradasProperties props;
    private final DeportivoClient deportivoClient;
    private final UsuariosClient usuariosClient;
    private final EntradaEventPublisher eventPublisher;
    private final MongoTemplate mongoTemplate;

    // ─────────────────────────────────────────────
    // ASIENTOS
    // ─────────────────────────────────────────────

    public List<AsientoDto> listarAsientos(String partidoId, String zona, EstadoAsiento estado) {
        requirePartidoExiste(partidoId);

        List<Asiento> asientos;
        if (zona != null && estado != null) {
            asientos = asientoRepo.findByPartidoIdAndZonaAndEstado(partidoId, zona, estado);
        } else if (zona != null) {
            asientos = asientoRepo.findByPartidoIdAndZona(partidoId, zona);
        } else if (estado != null) {
            asientos = asientoRepo.findByPartidoIdAndEstado(partidoId, estado);
        } else {
            asientos = asientoRepo.findByPartidoId(partidoId);
        }
        return asientos.stream().map(this::toAsientoDto).toList();
    }

    public AforoDto obtenerAforo(String partidoId) {
        requirePartidoExiste(partidoId);
        long total      = asientoRepo.countByPartidoId(partidoId);
        long ocupados   = asientoRepo.countByPartidoIdAndEstado(partidoId, EstadoAsiento.ocupado);
        long reservados = asientoRepo.countByPartidoIdAndEstado(partidoId, EstadoAsiento.reservado);
        long libres     = total - ocupados - reservados;
        return new AforoDto(partidoId, (int) total, (int) ocupados, (int) reservados, (int) libres);
    }

    public List<PrecioDto> listarPrecios(String partidoId) {
        requirePartidoExiste(partidoId);
        return tarifaRepo.findByPartidoId(partidoId).stream()
                .map(t -> new PrecioDto(t.getZona(), t.getTipoEntrada(), t.getPrecio()))
                .toList();
    }

    // ─────────────────────────────────────────────
    // RESERVAS
    // ─────────────────────────────────────────────

    /**
     * Crea una reserva bloqueando los asientos de forma atómica (todos o ninguno).
     *
     * Antes de bloquear se verifica con Deportivo que el partido existe y
     * tiene la venta abierta (llamada síncrona).
     *
     * Cada asiento pasa de libre a reservado con un updateFirst condicionado a que
     * siga libre: si dos usuarios compiten por el mismo asiento, solo uno modifica el documento.
     */
    public ReservaDto crearReserva(ReservaCreate req, String usuarioId) {
        // 1. Verificar partido en Deportivo (síncrono)
        DeportivoClient.PartidoInfo partido;
        try {
            partido = deportivoClient.obtenerPartido(req.partidoId());
        } catch (DeportivoClient.PartidoNoEncontradoException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "El partido no existe en Deportivo");
        } catch (DeportivoClient.DeportivoNoDisponibleException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo comprobar el partido; microservicio Deportivo no disponible");
        }

        // 2. Verificar que la venta está abierta
        if (!partido.ventaAbierta()) {
            throw new ApiException(HttpStatus.CONFLICT, "La venta de entradas para este partido está cerrada");
        }

        // 3. Sin duplicados en la lista de asientos
        List<String> asientoIds = req.asientos();
        if (new HashSet<>(asientoIds).size() != asientoIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La lista de asientos contiene duplicados");
        }

        // 4 y 5. Bloqueo atómico + creación de la reserva. Cualquier fallo en este bloque
        //        (asiento inexistente, ocupado, error de base de datos...) libera lo ya bloqueado.
        List<Asiento> asientosReservados = new ArrayList<>();
        List<String> asientosBloqueados = new ArrayList<>();
        try {
            for (String aid : asientoIds) {
                Asiento asiento = asientoRepo.findById(aid)
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                                "Asiento no encontrado: " + aid));

                if (!asiento.getPartidoId().equals(req.partidoId())) {
                    throw new ApiException(HttpStatus.NOT_FOUND,
                            "El asiento " + aid + " no pertenece al partido indicado");
                }

                if (asiento.getEstado() != EstadoAsiento.libre) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "El asiento " + aid + " no está libre (estado: " + asiento.getEstado() + ")");
                }

                // Operación atómica: solo modifica si el asiento SIGUE libre.
                var resultado = mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(aid)
                                .and("partidoId").is(req.partidoId())
                                .and("estado").is(EstadoAsiento.libre)),
                        new Update().set("estado", EstadoAsiento.reservado),
                        Asiento.class);
                if (resultado.getModifiedCount() != 1) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "El asiento " + aid + " ya no está libre; inténtelo de nuevo");
                }

                asiento.setEstado(EstadoAsiento.reservado);
                asientosReservados.add(asiento);
                asientosBloqueados.add(aid);
            }

            Instant ahora = Instant.now();
            Instant expira = ahora.plus(props.reservaTtlMinutes(), ChronoUnit.MINUTES);

            Reserva reserva = new Reserva();
            reserva.setReservaId(UUID.randomUUID().toString());
            reserva.setPartidoId(req.partidoId());
            reserva.setUsuarioId(usuarioId);
            reserva.setAsientoIds(asientoIds);
            reserva.setEstado(EstadoReserva.activa);
            reserva.setExpiraEn(expira);
            reserva.setCreadaEn(ahora);
            reservaRepo.save(reserva);

            List<AsientoDto> asientoDtos = asientosReservados.stream().map(this::toAsientoDto).toList();
            return new ReservaDto(reserva.getReservaId(), reserva.getPartidoId(),
                    reserva.getUsuarioId(), asientoDtos, expira);
        } catch (RuntimeException e) {
            liberarAsientosCondicionalmente(asientosBloqueados);   // todo o nada
            throw e;
        }
    }

    public ReservaDto obtenerReserva(String reservaId, String usuarioId, boolean isStaff) {
        Reserva reserva = requireReserva(reservaId);
        if (!isStaff && !reserva.getUsuarioId().equals(usuarioId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No tienes permiso para ver esta reserva");
        }
        List<AsientoDto> asientos = asientoRepo.findAllById(reserva.getAsientoIds())
                .stream().map(this::toAsientoDto).toList();
        return new ReservaDto(reserva.getReservaId(), reserva.getPartidoId(),
                reserva.getUsuarioId(), asientos, reserva.getExpiraEn());
    }

    /**
     * Cancela una reserva y libera los asientos.
     *
     * La transición activa → cancelada es atómica, así que los asientos se liberan una sola vez
     * aunque coincidan una cancelación, el scheduler de expiración o una compra.
     */
    public void cancelarReserva(String reservaId, String usuarioId, boolean isStaff) {
        Reserva reserva = requireReserva(reservaId);   // 404 si no existe

        if (!isStaff && !reserva.getUsuarioId().equals(usuarioId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No tienes permiso para cancelar esta reserva");
        }
        switch (reserva.getEstado()) {
            case pagada, pagando -> throw new ApiException(HttpStatus.CONFLICT,
                    "La reserva ya está pagada o tiene un pago en curso; anula las entradas individualmente");
            case cancelada, expirada -> { return; }   // idempotente → 204
            case activa -> { }
        }

        Reserva previa = mongoTemplate.findAndModify(
                porIdYEstado(reservaId, EstadoReserva.activa),
                new Update().set("estado", EstadoReserva.cancelada),
                Reserva.class);

        if (previa == null) {   // cambió de estado mientras tanto
            EstadoReserva actual = requireReserva(reservaId).getEstado();
            if (actual == EstadoReserva.cancelada || actual == EstadoReserva.expirada) {
                return;
            }
            throw new ApiException(HttpStatus.CONFLICT,
                    "La reserva ya está pagada o tiene un pago en curso");
        }
        liberarAsientosCondicionalmente(previa.getAsientoIds());   // solo una petición llega aquí
    }

    // ─────────────────────────────────────────────
    // ENTRADAS
    // ─────────────────────────────────────────────

    /**
     * Compra entradas a partir de una reserva vigente.
     *
     * Antes de calcular el precio consulta al microservicio Usuarios para
     * comprobar si el comprador es socio y obtener el descuento (síncrono).
     *
     * La reserva se "reclama" con la transición atómica activa → pagando: solo una
     * petición lo consigue, y mientras está pagando ni el scheduler ni una cancelación
     * pueden quitarle los asientos. La emisión es idempotente, así que una compra
     * interrumpida puede reanudarse sin duplicar entradas.
     *
     * Tras la compra se publica un evento "entrada comprada" por cada entrada.
     */
    public List<EntradaDto> comprarEntradas(CompraEntradas req, String usuarioId) {
        // 1. Obtener la reserva
        Reserva reserva = requireReserva(req.reservaId());

        // 2. Sólo el titular puede comprar
        if (!reserva.getUsuarioId().equals(usuarioId)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Solo el titular de la reserva puede comprar las entradas");
        }

        // 3. Según el estado actual de la reserva
        switch (reserva.getEstado()) {
            case pagada -> {
                // Reintento idempotente: mismos datos → mismas entradas (201); datos distintos → 409
                if (mismosDatos(reserva, req)) {
                    return entradasDeReserva(req.reservaId());
                }
                throw new ApiException(HttpStatus.CONFLICT,
                        "La reserva ya fue pagada con otros datos");
            }
            case cancelada -> throw new ApiException(HttpStatus.CONFLICT, "La reserva ha sido cancelada");
            case expirada  -> throw new ApiException(HttpStatus.GONE, "La reserva ha caducado");
            case pagando   -> { return reanudarPago(reserva, req); }
            case activa    -> { }
        }

        // 4. Reserva activa: ¿ha caducado por tiempo?
        if (Instant.now().isAfter(reserva.getExpiraEn())) {
            expirarReserva(reserva.getReservaId());
            throw new ApiException(HttpStatus.GONE, "La reserva ha caducado");
        }

        // 5. Consultar Usuarios para el descuento de socio (síncrono) y validar tarifas.
        //    Todo esto ocurre ANTES de tocar ningún estado, para no dejar compras a medias.
        double descuento = calcularDescuento(usuarioId, req.tipoEntrada());
        calcularLineas(reserva, req.tipoEntrada(), descuento);   // lanza 400 si falta alguna tarifa

        // 6. Reclamar la reserva: activa → pagando (atómico; solo una petición lo consigue)
        Instant ahora = Instant.now();
        Reserva previa = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(reserva.getReservaId())
                        .and("estado").is(EstadoReserva.activa)
                        .and("expiraEn").gt(ahora)),
                new Update()
                        .set("estado", EstadoReserva.pagando)
                        .set("tipoEntrada", req.tipoEntrada())
                        .set("metodoPago", req.metodoPago())
                        .set("descuento", descuento)
                        .set("pagandoDesde", ahora),
                Reserva.class);
        if (previa == null) {
            throw errorReservaNoReclamable(reserva.getReservaId());
        }

        // 7. Emitir entradas, marcar asientos ocupados y reserva pagada
        try {
            return emitirEntradas(reserva.getReservaId());
        } catch (RuntimeException e) {
            log.error("Fallo al emitir las entradas de la reserva {}", reserva.getReservaId(), e);
            // Si no se llegó a crear ninguna entrada, se devuelve la reserva a "activa" para poder reintentar ya.
            // Si hay entradas a medias se deja en "pagando": el reintento tras el lease las completa.
            if (entradaRepo.findByReservaId(reserva.getReservaId()).isEmpty()) {
                mongoTemplate.updateFirst(
                        porIdYEstado(reserva.getReservaId(), EstadoReserva.pagando),
                        new Update().set("estado", EstadoReserva.activa).unset("pagandoDesde"),
                        Reserva.class);
            }
            throw e;
        }
    }

    /** Reintento de una compra cuya reserva figura como "pagando". */
    private List<EntradaDto> reanudarPago(Reserva reserva, CompraEntradas req) {
        if (!mismosDatos(reserva, req)) {
            throw new ApiException(HttpStatus.CONFLICT, "Hay un pago en curso con otros datos");
        }
        Instant desde = reserva.getPagandoDesde();
        if (desde != null && desde.isAfter(Instant.now().minus(LEASE_PAGO))) {
            throw new ApiException(HttpStatus.CONFLICT, "Pago en curso");
        }
        // El pago anterior se quedó a medias: nos lo "reclamamos" renovando el lease (solo una petición lo logra)
        Reserva previa = mongoTemplate.findAndModify(
                Query.query(Criteria.where("_id").is(reserva.getReservaId())
                        .and("estado").is(EstadoReserva.pagando)
                        .and("pagandoDesde").is(desde)),
                new Update().set("pagandoDesde", Instant.now()),
                Reserva.class);
        if (previa == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Pago en curso");
        }
        return emitirEntradas(reserva.getReservaId());
    }

    /**
     * Crea las entradas, marca los asientos como ocupados y la reserva como pagada.
     * Es idempotente: si ya existe la entrada de un asiento no la duplica (además hay índice único).
     * Orden deliberado: primero la entrada, después el asiento ocupado, y al final la reserva pagada.
     */
    private List<EntradaDto> emitirEntradas(String reservaId) {
        Reserva reserva = requireReserva(reservaId);
        double descuento = reserva.getDescuento() == null ? 0.0 : reserva.getDescuento();
        List<Linea> lineas = calcularLineas(reserva, reserva.getTipoEntrada(), descuento);

        for (Linea linea : lineas) {
            String asientoId = linea.asiento().getAsientoId();

            if (entradaRepo.findByReservaIdAndAsientoId(reservaId, asientoId).isEmpty()) {
                Entrada entrada = new Entrada();
                entrada.setEntradaId(UUID.randomUUID().toString());
                entrada.setPartidoId(reserva.getPartidoId());
                entrada.setUsuarioId(reserva.getUsuarioId());
                entrada.setAsientoId(asientoId);
                entrada.setTipoEntrada(reserva.getTipoEntrada());
                entrada.setPrecioPagado(linea.precio());
                entrada.setDescuentoAplicado(descuento);
                entrada.setMetodoPago(reserva.getMetodoPago());
                entrada.setCodigoQr(generarCodigoQr());
                entrada.setEstado(EstadoEntrada.valida);
                entrada.setFechaCompra(Instant.now());
                entrada.setReservaId(reservaId);
                entrada.setEventoPublicado(false);
                try {
                    entradaRepo.save(entrada);
                } catch (DuplicateKeyException ignored) {
                    // otra petición ya la creó (índice único reserva+asiento): seguimos
                }
            }

            // Asiento reservado → ocupado (atómico y repetible)
            mongoTemplate.updateFirst(
                    porIdYEstado(asientoId, EstadoAsiento.reservado),
                    new Update().set("estado", EstadoAsiento.ocupado),
                    Asiento.class);
        }

        // Reserva pagando → pagada
        mongoTemplate.updateFirst(
                porIdYEstado(reservaId, EstadoReserva.pagando),
                new Update().set("estado", EstadoReserva.pagada).unset("pagandoDesde"),
                Reserva.class);

        // Evento "entrada comprada": si RabbitMQ falla, la compra ya está hecha y el job lo reintentará
        try {
            publicarEventosPendientes();
        } catch (RuntimeException e) {
            log.warn("No se pudieron publicar los eventos ahora; se reintentará: {}", e.getMessage());
        }

        return entradasDeReserva(reservaId);
    }

    public List<EntradaDto> listarEntradas(String usuarioId, String partidoId,
                                           String callerUserId, boolean isStaff) {
        if (!isStaff && !callerUserId.equals(usuarioId)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Solo puedes consultar tus propias entradas");
        }
        List<Entrada> entradas;
        if (partidoId != null) {
            entradas = entradaRepo.findByUsuarioIdAndPartidoId(usuarioId, partidoId);
        } else {
            entradas = entradaRepo.findByUsuarioId(usuarioId);
        }
        return entradas.stream()
                .map(e -> toEntradaDto(e, asientoRepo.findById(e.getAsientoId()).orElse(null)))
                .toList();
    }

    public EntradaDto obtenerEntrada(String entradaId, String callerUserId, boolean isStaff) {
        Entrada entrada = requireEntrada(entradaId);
        if (!isStaff && !entrada.getUsuarioId().equals(callerUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "No tienes permiso para ver esta entrada");
        }
        Asiento asiento = asientoRepo.findById(entrada.getAsientoId()).orElse(null);
        return toEntradaDto(entrada, asiento);
    }

    /**
     * Anula una entrada (solo staff/admin). La transición valida → anulada es atómica:
     * el asiento se libera una sola vez y repetir la anulación devuelve 204.
     * (El reembolso no está implementado: no hay pasarela de pago todavía.)
     */
    public void anularEntrada(String entradaId, boolean isStaff) {
        if (!isStaff) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo staff/admin puede anular entradas");
        }

        Entrada previa = mongoTemplate.findAndModify(
                porIdYEstado(entradaId, EstadoEntrada.valida),
                new Update().set("estado", EstadoEntrada.anulada),
                Entrada.class);

        if (previa == null) {
            Entrada actual = requireEntrada(entradaId);   // 404 si no existe
            if (actual.getEstado() == EstadoEntrada.anulada) {
                return;   // idempotente → 204
            }
            throw new ApiException(HttpStatus.CONFLICT, "La entrada ya fue utilizada y no puede anularse");
        }

        // Liberar el asiento (solo si sigue ocupado)
        mongoTemplate.updateFirst(
                porIdYEstado(previa.getAsientoId(), EstadoAsiento.ocupado),
                new Update().set("estado", EstadoAsiento.libre),
                Asiento.class);
        log.info("Entrada {} anulada; asiento {} liberado", entradaId, previa.getAsientoId());
    }

    // ─────────────────────────────────────────────
    // ACCESO
    // ─────────────────────────────────────────────

    /**
     * Valida una entrada en el acceso. La transición valida → usada es atómica:
     * si dos lectores escanean el mismo QR a la vez, solo uno obtiene 200 y el otro 409.
     */
    public EntradaDto validarEntrada(String entradaId, boolean isStaff) {
        if (!isStaff) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo staff puede validar entradas");
        }

        Entrada entrada = mongoTemplate.findAndModify(
                porIdYEstado(entradaId, EstadoEntrada.valida),
                new Update().set("estado", EstadoEntrada.usada),
                FindAndModifyOptions.options().returnNew(true),
                Entrada.class);

        if (entrada == null) {
            requireEntrada(entradaId);   // 404 si no existe
            throw new ApiException(HttpStatus.CONFLICT, "La entrada ya fue usada o está anulada");
        }

        Asiento asiento = asientoRepo.findById(entrada.getAsientoId()).orElse(null);
        return toEntradaDto(entrada, asiento);
    }

    // ─────────────────────────────────────────────
    // SCHEDULERS
    // ─────────────────────────────────────────────

    /**
     * Limpia las reservas activas que han superado su TTL y libera los asientos.
     * Se ejecuta cada minuto. La transición activa → expirada es atómica, así que
     * no compite con una compra (activa → pagando) ni con una cancelación.
     */
    @Scheduled(fixedDelay = 60_000)
    public void limpiarReservasExpiradas() {
        List<Reserva> expiradas = reservaRepo.findByEstadoAndExpiraEnBefore(
                EstadoReserva.activa, Instant.now());

        for (Reserva r : expiradas) {
            try {
                Reserva previa = mongoTemplate.findAndModify(
                        Query.query(Criteria.where("_id").is(r.getReservaId())
                                .and("estado").is(EstadoReserva.activa)
                                .and("expiraEn").lt(Instant.now())),
                        new Update().set("estado", EstadoReserva.expirada),
                        Reserva.class);
                if (previa != null) {
                    log.info("Liberando reserva expirada {}", r.getReservaId());
                    liberarAsientosCondicionalmente(previa.getAsientoIds());
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo expirar la reserva {}: {}", r.getReservaId(), e.getMessage());
            }
        }
    }

    /**
     * Publica en RabbitMQ el evento "entrada comprada" de las entradas pendientes
     * (outbox mínimo). Entrega al menos una vez: el consumidor debe deduplicar por entradaId.
     * Se llama tras cada compra y, por si RabbitMQ estaba caído, cada 30 segundos.
     */
    @Scheduled(fixedDelay = 30_000)
    public void publicarEventosPendientes() {
        List<Entrada> pendientes = entradaRepo.findByEventoPublicadoFalseAndEstadoNot(EstadoEntrada.anulada);

        for (Entrada entrada : pendientes) {
            try {
                Asiento asiento = asientoRepo.findById(entrada.getAsientoId()).orElse(null);
                eventPublisher.publicarEntradaComprada(
                        EntradaEventPublisher.EntradaCompradaEvent.desde(
                                entrada.getEntradaId(),
                                entrada.getUsuarioId(),
                                entrada.getFechaCompra(),
                                entrada.getPrecioPagado(),
                                asiento != null ? asiento.getZona() : "",
                                asiento != null ? asiento.getFila() : 0,
                                asiento != null ? asiento.getNumero() : 0));
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("_id").is(entrada.getEntradaId())),
                        new Update().set("eventoPublicado", true),
                        Entrada.class);
            } catch (RuntimeException e) {
                // Broker caído: no seguimos (evita esperar un timeout por cada entrada) y se reintenta luego
                log.warn("No se pudo publicar el evento de la entrada {}: {}", entrada.getEntradaId(), e.getMessage());
                break;
            }
        }
    }

    // ─────────────────────────────────────────────
    // HELPERS PRIVADOS
    // ─────────────────────────────────────────────

    /** Línea de compra: un asiento y el precio final que le corresponde. */
    private record Linea(Asiento asiento, double precio) {}

    /**
     * Calcula el precio de cada asiento de la reserva. No modifica nada, por lo que
     * se puede llamar antes de empezar la compra para validar que existen todas las tarifas.
     */
    private List<Linea> calcularLineas(Reserva reserva, TipoEntrada tipo, double descuento) {
        List<Linea> lineas = new ArrayList<>();
        for (String asientoId : reserva.getAsientoIds()) {
            Asiento asiento = asientoRepo.findById(asientoId)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                            "Asiento no encontrado: " + asientoId));

            double precioBase = tarifaRepo
                    .findByPartidoIdAndZonaAndTipoEntrada(reserva.getPartidoId(), asiento.getZona(), tipo)
                    .map(Tarifa::getPrecio)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                            "No se encontró tarifa para zona " + asiento.getZona() + " y tipo " + tipo));

            double precioFinal = Math.round(precioBase * (1 - descuento / 100.0) * 100.0) / 100.0;
            lineas.add(new Linea(asiento, precioFinal));
        }
        return lineas;
    }

    /**
     * Consulta síncrona a Usuarios: devuelve el porcentaje de descuento a aplicar.
     *
     * OJO: ahora se aplica el descuento de socio a cualquier tipo de entrada si el usuario es
     * socio activo (incluido tipoEntrada=socio, cuya tarifa ya es más barata). Revisa en tu
     * memoria si el descuento solo debe aplicarse a las entradas "general".
     */
    private double calcularDescuento(String usuarioId, TipoEntrada tipo) {
        try {
            UsuariosClient.SocioInfo socioInfo = usuariosClient.obtenerInfoSocio(usuarioId);
            if (tipo == TipoEntrada.socio && !socioInfo.socioActivo()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "El usuario no es socio activo; no puede comprar entrada de tipo socio");
            }
            return socioInfo.socioActivo() ? socioInfo.descuento() : 0.0;
        } catch (ApiException e) {
            throw e;
        } catch (UsuariosClient.UsuarioNoEncontradoException e) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "El usuario no existe según el microservicio Usuarios");
        } catch (UsuariosClient.UsuariosNoDisponibleException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo comprobar el estado de socio; microservicio Usuarios no disponible");
        }
    }

    private boolean mismosDatos(Reserva reserva, CompraEntradas req) {
        return req.tipoEntrada() == reserva.getTipoEntrada()
                && req.metodoPago() == reserva.getMetodoPago();
    }

    /** Explica por qué no se pudo reclamar la reserva (otra petición se adelantó, caducó, se canceló...). */
    private ApiException errorReservaNoReclamable(String reservaId) {
        return switch (requireReserva(reservaId).getEstado()) {
            case activa, expirada -> new ApiException(HttpStatus.GONE, "La reserva ha caducado");
            case cancelada        -> new ApiException(HttpStatus.CONFLICT, "La reserva ha sido cancelada");
            case pagando, pagada  -> new ApiException(HttpStatus.CONFLICT,
                    "Pago en curso o reserva ya procesada por otra petición");
        };
    }

    /** activa → expirada (atómico) y liberación de asientos, una sola vez. */
    private void expirarReserva(String reservaId) {
        Reserva previa = mongoTemplate.findAndModify(
                porIdYEstado(reservaId, EstadoReserva.activa),
                new Update().set("estado", EstadoReserva.expirada),
                Reserva.class);
        if (previa != null) {
            liberarAsientosCondicionalmente(previa.getAsientoIds());
        }
    }

    private List<EntradaDto> entradasDeReserva(String reservaId) {
        return entradaRepo.findByReservaId(reservaId).stream()
                .map(e -> toEntradaDto(e, asientoRepo.findById(e.getAsientoId()).orElse(null)))
                .toList();
    }

    private void requirePartidoExiste(String partidoId) {
        if (!asientoRepo.existsByPartidoId(partidoId)) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "No existe ningún partido con ese identificador en este servicio: " + partidoId);
        }
    }

    private Reserva requireReserva(String reservaId) {
        return reservaRepo.findById(reservaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Reserva no encontrada: " + reservaId));
    }

    private Entrada requireEntrada(String entradaId) {
        return entradaRepo.findById(entradaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "Entrada no encontrada: " + entradaId));
    }

    /** Query "documento con este _id y en este estado": base de todas las transiciones atómicas. */
    private static Query porIdYEstado(String id, Object estado) {
        return Query.query(Criteria.where("_id").is(id).and("estado").is(estado));
    }

    /** Libera solo los asientos que siguen en estado "reservado" (nunca uno ya vendido o de otro). */
    private void liberarAsientosCondicionalmente(List<String> asientoIds) {
        if (asientoIds == null || asientoIds.isEmpty()) {
            return;
        }
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("_id").in(asientoIds)
                        .and("estado").is(EstadoAsiento.reservado)),
                new Update().set("estado", EstadoAsiento.libre),
                Asiento.class);
    }

    private String generarCodigoQr() {
        // En producción se generaría un código QR real (por ejemplo firmado con HMAC).
        return "QR-" + UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 16);
    }

    private AsientoDto toAsientoDto(Asiento a) {
        return new AsientoDto(a.getAsientoId(), a.getZona(), a.getFila(), a.getNumero(), a.getEstado());
    }

    private EntradaDto toEntradaDto(Entrada e, Asiento asiento) {
        AsientoDto asientoDto = asiento != null ? toAsientoDto(asiento) : null;
        return new EntradaDto(
                e.getEntradaId(),
                e.getPartidoId(),
                e.getUsuarioId(),
                asientoDto,
                e.getTipoEntrada(),
                e.getPrecioPagado(),
                e.getDescuentoAplicado(),
                e.getMetodoPago(),
                e.getCodigoQr(),
                e.getEstado(),
                e.getFechaCompra()
        );
    }
}