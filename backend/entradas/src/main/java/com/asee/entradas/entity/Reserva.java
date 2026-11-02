package com.asee.entradas.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.List;

/**
 * Documento MongoDB que representa una reserva temporal de asientos.
 *
 * La reserva bloquea los asientos de forma atómica durante un tiempo limitado
 * (configurado en entradas.reserva-ttl-minutes). Una vez expirada o cancelada,
 * los asientos quedan libres para otro comprador.
 *
 * Ciclo de vida (cada transición se hace con una operación atómica en MongoDB):
 * activa → pagando → pagada
 * activa → cancelada | expirada
 * pagando → activa (si la emisión falla antes de crear ninguna entrada)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reservas")
public class Reserva {

    @Id
    private String reservaId;

    @Indexed
    private String partidoId;

    @Indexed
    private String usuarioId;

    /** IDs de los asientos bloqueados. */
    private List<String> asientoIds;

    private EstadoReserva estado = EstadoReserva.activa;

    /** Momento en que expira la reserva y los asientos quedan libres. */
    private Instant expiraEn;

    private Instant creadaEn = Instant.now();

    // ── Datos de la compra (se rellenan al pasar a "pagando") ──────────────

    /** Tipo de entrada elegido en la compra; sirve para detectar reintentos con datos distintos. */
    private TipoEntrada tipoEntrada;

    private MetodoPago metodoPago;

    /** Porcentaje de descuento calculado al iniciar la compra. */
    private Double descuento;

    /** Cuándo empezó el pago en curso (permite recuperar compras que quedaron a medias). */
    private Instant pagandoDesde;
}